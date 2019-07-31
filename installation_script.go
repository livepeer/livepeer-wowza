package main

import (
	"bufio"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"io/ioutil"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strings"

	"github.com/golang/glog"
	"github.com/jbowtie/gokogiri"
	"github.com/jbowtie/gokogiri/xml"
)

type version struct {
	Version string `json:"version"`
	URL     string `json:"url"`
}

func main() {
	flag.Set("logtostderr", "true")
	wowzaDir := flag.String("wowzaDir", "", "path to WowzaStreamingEngine folder")
	channel := flag.String("channel", "latest", "branch name for latest version of JAR file")
	apiKey := flag.String("apikey", "", "livepeer api key")
	flag.Parse()

	// Set wowza default directory appropriate for each operating system
	if *wowzaDir == "" {
		if strings.Contains(runtime.GOOS, "windows") {
			*wowzaDir = "/Program Files (x86)/Wowza Media Systems/Wowza Streaming Engine 4.7.7/  "
			fmt.Println("Running on a Windows operating system")
		}
		if strings.Contains(runtime.GOOS, "linux") {
			*wowzaDir = "/usr/local/WowzaStreamingEngine/"
			fmt.Println("Running on a linux operating system")
		}
		if strings.Contains(runtime.GOOS, "darwin") {
			*wowzaDir = "/Library/WowzaStreamingEngine/"
			fmt.Println("Running on a mac operating system")
		}
	}

	// If no API key provided, look for one in xml or prompt user for key, then save in XML file
	serverFilePath := filepath.Join(*wowzaDir, "conf/Server.xml")

	_, err := os.Stat(serverFilePath)
	if err == nil {
		key, err := findAndSaveAPIKey(serverFilePath, *apiKey)
		if err != nil {
			panic(err)
		}
		*apiKey = key
	} else {
		panic(err)
	}

	// Download latest JAR file to disk
	err = downloadLatestJarFile(*channel, *wowzaDir)
	if err != nil {
		panic(err)
	}

	// Insert Livepeer Wowza module information into all Application.xml files
	err = filepath.Walk(*wowzaDir, insertLivepeerWowzaModule)
	if err != nil {
		panic(err)
	}
}

func findAndSaveAPIKey(serverFilePath string, apiKey string) (string, error) {
	// Create searchable xml document
	doc, err := createSearchableXMLDocument(serverFilePath)
	if err != nil {
		return "", fmt.Errorf("Error creating searable XML serverFilePath: %v", err)
	}

	// Search for properties tag and extract API key
	propertiesNodes, err := doc.Search("/Root/Server/Properties")
	if err != nil {
		return "", fmt.Errorf("Property node search error: %v", err)
	}

	if len(propertiesNodes) < 1 {
		return "", fmt.Errorf("<Properties> tag not found %v", err)
	}

	for _, propertiesNode := range propertiesNodes {
		nodes, err := propertiesNode.Search("Property[Name='livepeer.org/api-key']")
		if err != nil {
			return "", fmt.Errorf("Property node search error: %v", err)
		}
		if len(nodes) > 0 {
			valueNodes, err := nodes[0].Search("Value")
			if err != nil {
				return "", fmt.Errorf("Searching for Value error: %v", err)
			}
			apiKey = valueNodes[0].InnerHtml()
			fmt.Printf("Livepeer API key in Server.XML: %s\n", apiKey)
			return apiKey, nil
			break
		}
	}

	if apiKey == "" {
		// If apiKey not found, prompt user for API key
		for apiKey == "" {
			apiKey = promptUserForAPIKey()

			// TODO: check for valid API key in the future
			if apiKey == "" {
				glog.Error("Invalid API key provided")
				continue
			}
		}
	}
	// Add API key to XML document
	propertiesNodes[0].AddChild(fmt.Sprintf(
		`	<Property>
				<Name>livepeer.org/api-key</Name>
				<Value>%s</Value>
				<Type>String</Type>
			</Property>
		`, apiKey))

	err = ioutil.WriteFile(serverFilePath, []byte(doc.String()), 0644)
	if err != nil {
		return "", fmt.Errorf("Failed to write API key to Server.xml file, error: %v", err)
	}
	fmt.Printf("Livepeer API key inserted into Server.XML: %s\n", apiKey)

	return apiKey, nil
}

func createSearchableXMLDocument(filePath string) (*xml.XmlDocument, error) {
	xmlFile, err := os.Open(filePath)
	if err != nil {
		glog.Error("Could not open xml file, error: ", err)
		return nil, err
	}

	defer xmlFile.Close()

	// Open xml file as a byte array
	serverBytes, err := ioutil.ReadAll(xmlFile)
	if err != nil {
		return nil, fmt.Errorf("Could not read opened xml as a byte array, error: %v", err)
	}

	// Create searchable xml document
	doc, err := gokogiri.ParseXml(serverBytes)
	if err != nil {
		return nil, fmt.Errorf("Could not create searchable XML document, parsing error: %v", err)
	}

	return doc, nil
}

func promptUserForAPIKey() string {
	reader := bufio.NewReader(os.Stdin)
	fmt.Print("No API key provided - please enter your Livepeer API key: ")
	key, _ := reader.ReadString('\n')
	return strings.Trim(key, "\n")
}

func downloadLatestJarFile(channel string, wowzaDir string) error {
	jsonVersionLink := fmt.Sprintf("https://build.livepeer.live/LivepeerWowza/%s.json", channel)

	// Download json info on latest LivepeerWoza JAR file
	latestURL, err := downloadVersion(jsonVersionLink)
	if err != nil {
		return fmt.Errorf("Download json file error: %v", err)
	}
	// Dowload latest LivepeerWowza JAR file
	if err := downloadFile(wowzaDir, latestURL); err != nil {
		return fmt.Errorf("Download latest .jar file error: %v", err)
	}

	fmt.Printf("Livepeer .jar file downloaded into: %s\n", wowzaDir)

	return nil
}

func downloadVersion(url string) (string, error) {
	// Get data from URL
	resp, err := http.Get(url)
	if err != nil || resp.StatusCode != 200 {
		return "", fmt.Errorf("Http.Get error: %v ", err)
	}
	defer resp.Body.Close()

	// Read json info on latest LivepeerWoza JAR file
	versionBytes, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("File ReadAll error: %v", err)
	}

	var latest version
	err = json.Unmarshal(versionBytes, &latest)
	if err != nil {
		return "", fmt.Errorf("Json unmarshal error: %v", err)
	}

	return latest.URL, err
}

func downloadFile(dir string, url string) error {
	// Insert Wowza .jar file into Wowza directory
	filePath := filepath.Join(dir, filepath.Base(url))
	out, err := os.Create(filePath)
	if err != nil {
		return fmt.Errorf("Os.Create error: %v ", err)
	}
	defer out.Close()

	// Change file permissions
	err = out.Chmod(0777)

	// Get data from URL
	resp, err := http.Get(url)
	if err != nil || resp.StatusCode != 200 {
		glog.Error("Http.Get error code: ", resp.StatusCode)
		return fmt.Errorf("Http.Get error: %v ", err)
	}
	defer resp.Body.Close()

	// Write the body to file
	_, err = io.Copy(out, resp.Body)
	if err != nil {
		return fmt.Errorf("Write body to file error: %v ", err)
	}

	return err
}

func insertLivepeerWowzaModule(path string, f os.FileInfo, err error) error {
	if filepath.Base(path) == "Application.xml" && !strings.Contains(path, "examples") {
		err = insertModule(path)
		return err
	}
	return nil
}

func insertModule(appFilePath string) error {
	if _, err := os.Stat(appFilePath); err != nil {
		return fmt.Errorf("Could not find Application.xml file, error: %v", err)
	}

	doc, err := createSearchableXMLDocument(appFilePath)
	if err != nil {
		return fmt.Errorf("Error creating searable XML application.xml: %v", err)
	}

	modulesNodes, err := doc.Search("/Root/Application/Modules")
	if err != nil {
		return fmt.Errorf("Modules node search error: %v", err)
	}

	// Return if Livepeer module has already been inserted
	for _, modulesNodes := range modulesNodes {
		nodes, err := modulesNodes.Search("Module[Name='LivepeerWowza']")
		if err != nil {
			return fmt.Errorf("Modules node search error: %v", err)
		}
		if len(nodes) > 0 {
			fmt.Printf("Livepeer module in: %v \n", appFilePath)
			return nil
		}
	}

	// Add module to XML document, properly aligned
	modulesNodes[0].AddChild(fmt.Sprint(
		`	<Module>
			<Name>LivepeerWowza</Name>
			<Description>Offloads Wowza transcoding to the Livepeer network</Description>
			<Class>org.livepeer.LivepeerWowza.ModuleLivepeerWowza</Class>
		</Module>
	`))

	err = ioutil.WriteFile(appFilePath, []byte(doc.String()), 0644)
	if err != nil {
		return fmt.Errorf("Failed to write module to XML file, error: %v", err)
	}
	fmt.Print("Livepeer module inserted into: ", appFilePath)

	return nil

}

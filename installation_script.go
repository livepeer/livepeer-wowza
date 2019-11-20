package main

import (
	"bufio"
	"encoding/json"
	"errors"
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
	"github.com/manifoldco/promptui"
	"github.com/mattn/go-isatty"
)

type version struct {
	Version string `json:"version"`
	URL     string `json:"url"`
}

func main() {
	flag.Set("logtostderr", "true")
	application := flag.String("application", "", "comma separated list of applications on server to update")
	wowzaDir := flag.String("wowzaDir", "", "path to WowzaStreamingEngine folder")
	channel := flag.String("channel", "latest", "branch name for latest version of JAR file")
	apiKey := flag.String("apikey", "", "livepeer api key")
	flag.Parse()

	terminal := false
	if isatty.IsTerminal(os.Stdout.Fd()) || isatty.IsCygwinTerminal(os.Stdout.Fd()) {
		terminal = true
	}

	// Set wowza default directory appropriate for each operating system
	if *wowzaDir == "" {
		if strings.Contains(runtime.GOOS, "windows") {
			*wowzaDir = "/Program Files (x86)/Wowza Media Systems/Wowza Streaming Engine 4.7.7/"
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
		key, err := findAndSaveAPIKey(serverFilePath, *apiKey, terminal)
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

	// If user has not set the application flag, find all existing application names
	appPaths := []string{}
	if *application == "" {
		apps, _ := ioutil.ReadDir(filepath.Join(*wowzaDir, "applications/"))
		for _, app := range apps {
			appPaths = append(appPaths, app.Name())
		}
		*application = strings.Join(appPaths, ",")
	}

	// Prompt user to confirm applications to update
	err = promptUserForInsertLocation(*application)
	if err != nil {
		panic(err)
	}

	// Insert Livepeer Wowza module information into selected Application.xml files
	apps := strings.Split(*application, ",")
	for _, app := range apps {
		updatePath := filepath.Join(*wowzaDir, "conf/", app)
		err = filepath.Walk(updatePath, insertLivepeerWowzaModule)
		if err != nil {
			panic(err)
		}
	}
}

func promptUserForInsertLocation(apps string) error {
	prompt := promptui.Select{
		Label: fmt.Sprintf(`Would you like to install the Wowza Livepeer Module in these locations?: [%s]`, apps),
		Items: []string{"Yes", "No"},
	}

	_, result, err := prompt.Run()

	if err != nil {
		return err
	}

	if result == "No" {
		err := errors.New(`
		No application selected. Please re-run script with the '-application' flag,
		and provide comma separated list of applications to update with Livepeer module`)
		return err
	}

	return nil
}

func findAndSaveAPIKey(serverFilePath string, apiKey string, terminal bool) (string, error) {
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

	var valueNodes []xml.Node
	for _, propertiesNode := range propertiesNodes {
		nodes, err := propertiesNode.Search("Property[Name='livepeer.org/api-key']")
		if err != nil {
			return "", fmt.Errorf("Property node search error: %v", err)
		}
		if len(nodes) > 0 {
			valueNodes, err = nodes[0].Search("Value")
			if err != nil {
				return "", fmt.Errorf("Searching for Value error: %v", err)
			}
			apiKey = valueNodes[0].InnerHtml()
			fmt.Printf("Livepeer API key in Server.XML: %s\n", apiKey)
		}
	}

	if apiKey == "" && terminal {
		// If apiKey not found, prompt user for API key
		apiKey = promptUserForAPIKey(apiKey)
		// Add API key to XML document
		propertiesNodes[0].AddChild(fmt.Sprintf(
			`	<Property>
					<Name>livepeer.org/api-key</Name>
					<Value>%s</Value>
					<Type>String</Type>
				</Property>
			`, apiKey))
	} else if terminal {
		key := promptUserChangeAPIKey()
		if key == "" {
			return apiKey, nil
		}
		apiKey = key
		// Add new API key to XML document
		valueNodes[0].SetInnerHtml(apiKey)
	}

	if apiKey == "" {
		fmt.Printf("Failed to write API key, no key saved or provided \n")
		return "", nil
	}

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

func promptUserForAPIKey(apiKey string) string {
	// If apiKey not found, prompt user for API key
	for apiKey == "" {
		reader := bufio.NewReader(os.Stdin)
		fmt.Print("No API key provided - please enter your Livepeer API key: ")
		apiKey, _ = reader.ReadString('\n')
		apiKey = strings.Trim(apiKey, "\n")

		// TODO: check for valid API key in the future
		if apiKey == "" {
			glog.Error("Invalid API key provided")
			continue
		} else {
			break
		}
	}
	return strings.Trim(apiKey, "\n")
}

func promptUserChangeAPIKey() string {
	apiKey := ""
	resp := ""
	for {
		reader := bufio.NewReader(os.Stdin)
		fmt.Print("API key already provided - would you like to enter a new Livepeer API key? (y/n)")
		resp, _ = reader.ReadString('\n')
		resp = strings.Trim(resp, "\n")

		if resp == "y" {
			apiKey = promptUserForAPIKey("")
			break
		} else if resp == "n" {
			break
		} else {
			glog.Error("Invalid option selected. Please enter either 'y' or 'n'")
		}
	}
	return apiKey
}

func downloadLatestJarFile(channel string, wowzaDir string) error {
	jsonVersionLink := fmt.Sprintf("https://build.livepeer.live/LivepeerWowza/%s.json", channel)

	// Download json info on latest LivepeerWoza JAR file
	latestURL, err := downloadVersion(jsonVersionLink)
	if err != nil {
		return fmt.Errorf("Download json file error: %v", err)
	}

	// Dowload latest LivepeerWowza JAR file
	jarFilePath := filepath.Join(wowzaDir, "lib/")
	if err := downloadFile(jarFilePath, latestURL); err != nil {
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
	err = out.Chmod(0755)

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
		nodes, err := modulesNodes.Search("Module[Class='org.livepeer.LivepeerWowza.ModuleLivepeerWowza']")
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

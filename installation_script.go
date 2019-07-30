package main

import (
	"bufio"
	"encoding/json"
	"encoding/xml"
	"errors"
	"flag"
	"fmt"
	"io/ioutil"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strings"

	"github.com/golang/glog"
	"github.com/jbowtie/gokogiri"
)

type Server struct {
	XMLRoot    xml.Name   `xml:"Root"`
	Server     xml.Name   `xml:"Server"`
	Properties []Property `xml:"Properties"`
}

type Property struct {
	Name  string `xml:Name`
	Value string `xml:Value`
	Type  string `xml:String`
}

type Version struct {
	version string
	url     string
}

func main() {
	// resp, _ := http.Get("http://www.google.com")
	// page, _ := ioutil.ReadAll(resp.Body)

	// // parse the web page
	// doc, _ := gokogiri.ParseHtml(page)

	// // perform operations on the parsed page -- consult the tests for examples

	// // important -- don't forget to free the resources when you're done!
	// glog.Error("HERE IS THE TEXT: ", doc.ToText())
	// doc.Free()

	// 	input := "<foo></foo>"
	// 	expected := `<?xml version="1.0" encoding="utf-8"?>
	// <foo/>
	// `
	// 	doc, err := gokogiri.ParseXml([]byte(input))
	// 	if err != nil {
	// 		glog.Error("Parsing has error:", err)
	// 		return
	// 	}

	// 	if doc.String() != expected {
	// 		glog.Error("the output of the xml doc does not match the expected")
	// 	}

	// 	expected = `<?xml version="1.0" encoding="utf-8"?>
	// <foo>
	//   <bar/>
	// </foo>
	// `
	// 	doc.Root().AddChild("<bar/>")
	// 	if doc.String() != expected {
	// 		glog.Error("the output of the xml doc does not match the expected")
	// 	}
	// 	glog.Error(doc.String())
	// 	doc.Free()

	// 	return

	flag.Set("logtostderr", "true")
	wowzaDir := flag.String("wowzaDir", "", "path to WowzaStreamingEngine folder")
	channel := flag.String("channel", "latest", "branch name for latest version of JAR file")
	apiKey := flag.String("apiKey", "", "livepeer api key")
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

	// If no API key was provided, look for one in xml or prompt user for key
	if *apiKey == "" {
		confDir := filepath.Join(*wowzaDir, "conf/Server.xml")

		if _, err := os.Stat(confDir); err == nil {
			key, err := findAndSaveApiKey(confDir)
			if err != nil {
				panic(err)
			}
			*apiKey = key
		}
	}

	// Download latest JAR file to disk
	err := downloadLatestJarFile(*channel, *wowzaDir)
	if err != nil {
		panic(err)
	}

}

func findAndSaveApiKey(confDir string) (string, error) {

	// Look for api key in server.xml
	serverXml, err := os.Open(confDir)
	if err != nil {
		glog.Error("Could not open server xml file, error: ", err)
		return "", err
	}

	fmt.Println("Successfully Opened Server.xml")
	defer serverXml.Close()

	// Open xml file as a byte array
	serverBytes, err := ioutil.ReadAll(serverXml)
	if err != nil {
		glog.Error("Could not read opened server.xml as a byte array, error: ", serverBytes)
		return "", err
	}

	doc, err := gokogiri.ParseXml(serverBytes)
	if err != nil {
		glog.Error("Parsing has error:", err)
		return "", err
	}

	propertiesNodes, err := doc.Search("/Root/Server/Properties")
	if err != nil {
		glog.Error("Property node search has error:", err)
		return "", err
	}
	if len(propertiesNodes) < 1 {
		glog.Error("<Properties> tag not found")
		return "", errors.New("<Properties> tag not found")
	}

	var apiKey string
	for _, propertiesNode := range propertiesNodes {
		nodes, err := propertiesNode.Search("Property[Name='livepeer.org/api-key']")
		if err != nil {
			glog.Error("Property node search has error:", err)
			return "", err
		}
		if len(nodes) > 0 {
			valueNodes, err := nodes[0].Search("Value")
			if err != nil {
				glog.Error("Searching for Value as error:", err)
				return "", err
			}
			apiKey = valueNodes[0].InnerHtml()
			break
		}
	}

	if apiKey == "" {
		for apiKey == "" {
			apiKey = promptUserForAPIKey()

			// TODO: check for valid API key in the future
			if apiKey == "" {
				glog.Error("Invalid API key provided")
				continue
			}
		}

		propertiesNode := propertiesNodes[0]
		propertiesNode.AddChild(fmt.Sprintf(`
			<Property>
				<Name>livepeer.org/api-key</Name>
				<Value>%s</Value>
				<Type>String</Type>
			</Property>
		`, apiKey))
		// TODO : FIX ... this probably doesn't write the comlete file :(
		err = ioutil.WriteFile(confDir, []byte(doc.String()), 0644)
		if err != nil {
			glog.Error("failed to write API key to Server.xml file, error: ", err)
		}
	}

	return apiKey, nil

	// glog.Error("Doc:", doc.String())
	// glog.Error("Search stuff:", stuff)
	// if err != nil {
	// 	glog.Error("Parsing has error:", err)
	// 	return "", err
	// }

	// // Check for API key in server.xml
	// var key string
	// var server Server
	// // TODO: UNMARSHALING NOT WORKING PROPERLY
	// err = xml.Unmarshal(serverBytes, &server)
	// if err != nil {
	// 	glog.Error("Could not unmarshal xml bytes, err: ", err)
	// 	return "", err
	// }

	// for i := 0; i < len(server.Properties); i++ {
	// 	glog.Error("HERE ARE PROPERTIES: ", server.Properties[i])
	// 	if strings.Contains("livepeer.org/api-key", server.Properties[i].Name) {
	// 		key = server.Properties[i].Value
	// 		break
	// 	}
	// }

	// // If no API key in xml file, prompt user for key in CLI

	// return xmlKey, nil
}

func promptUserForAPIKey() string {
	reader := bufio.NewReader(os.Stdin)
	fmt.Print("No API key provided - please enter your Livepeer API key:")
	key, _ := reader.ReadString('\n')
	return strings.Trim(key, "\n")
}

func downloadLatestJarFile(channel string, wowzaDir string) error {
	jsonVersionLink := fmt.Sprintf("https://build.livepeer.live/LivepeerWowza/%s.json", channel)
	jsonLocalFilePath := fmt.Sprintf("lib/%s.json", channel)

	// Download json info on latest LivepeerWoza JAR file
	if err := downloadFile(jsonLocalFilePath, jsonVersionLink); err != nil {
		return err
	}

	// Open json info on latest LivepeerWoza JAR file
	latestFile, err := os.Open(jsonLocalFilePath)
	if err != nil {
		return err
	}

	// Read json info on latest LivepeerWoza JAR file
	versionBytes, err := ioutil.ReadAll(latestFile)
	if err != nil {
		return err
	}

	var version []Version
	err = json.Unmarshal(versionBytes, &version)
	if err != nil {
		return err
	}

	// Dowload latest LivepeerWowza JAR file
	if err := downloadFile(wowzaDir, version[0].url); err != nil {
		return err
	}

	return nil
}

// downloadFile will download a url to a local file. It's efficient because it will
// write as it downloads and not load the whole file into memory.
func downloadFile(path string, url string) error {
	// Create directory
	if err := os.MkdirAll(filepath.Dir(path), os.ModePerm); err != nil {
		glog.Error("error: ", err)
		return err
	}

	// Get the data
	resp, err := http.Get(url)
	if err != nil {
		glog.Error("error: ", err)
		return err
	}
	defer resp.Body.Close()

	// Create file
	out, err := os.Create(path)
	if err != nil {
		glog.Error("error: ", err)
		return err
	}
	defer out.Close()

	// Change file permissions
	err = out.Chmod(755)

	return err
}

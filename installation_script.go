package main

import (
	"bufio"
	"encoding/json"
	"encoding/xml"
	"flag"
	"fmt"
	"io/ioutil"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strings"

	"github.com/golang/glog"
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

	// Check for API key in server.xml
	var key string
	var server Server
	// TODO: UNMARSHALING NOT WORKING PROPERLY
	err = xml.Unmarshal(serverBytes, &server)
	if err != nil {
		glog.Error("Could not unmarshal xml bytes, err: ", err)
		return "", err
	}

	for i := 0; i < len(server.Properties); i++ {
		glog.Error("HERE ARE PROPERTIES: ", server.Properties[i])
		if strings.Contains("livepeer.org/api-key", server.Properties[i].Name) {
			key = server.Properties[i].Value
			break
		}
	}

	// If no API key in xml file, prompt user for key in CLI
	var xmlKey string
	if key == "" {
		for xmlKey == "" {
			xmlKey := promptUserForAPIKey()

			// TODO: check for valid API key in the future
			if xmlKey == "" {
				glog.Error("Invalid API key provided")
				continue
			}

			// places valid API key in XML
			keyProperty := Property{Name: "livepeer.org/api-key", Value: xmlKey}
			server.Properties = append(server.Properties, keyProperty)
			bytes, err := xml.Marshal(server)
			if err != nil {
				glog.Error("failed to marshal Server.xml file, error: ", err)
			}

			// TODO : FIX ... this probably doesn't write the comlete file :(
			err = ioutil.WriteFile(confDir, bytes, 0644)
			if err != nil {
				glog.Error("failed to write API key to Server.xml file, error: ", err)
			}
		}
	}

	return xmlKey, nil
}

func promptUserForAPIKey() string {
	reader := bufio.NewReader(os.Stdin)
	fmt.Print("No API key provided - please enter your Livepeer API key:")
	key, _ := reader.ReadString('\n')
	return key
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

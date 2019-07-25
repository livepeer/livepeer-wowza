package main

import (
	"net/http"
	"os"
	"path/filepath"

	"github.com/golang/glog"
)

func main() {

	fileURL := "https://build.livepeer.live/LivepeerWowza/0.0.1/LivepeerWowza.jar"

	if err := DownloadFile("lib/LivepeerWowza.jar", fileURL); err != nil {
		panic(err)
	}

}

// DownloadFile will download a url to a local file. It's efficient because it will
// write as it downloads and not load the whole file into memory.
func DownloadFile(path string, url string) error {
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

	// Create the file
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

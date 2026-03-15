package main

import "github.com/ril3y/freewheel-jailbreak/internal/ui"

// Set via ldflags: -ldflags "-X main.version=1.0.0 -X main.gitHash=abc12345"
var version = "dev"
var gitHash = "unknown"

func main() {
	ui.Version = version
	ui.GitHash = gitHash
	ui.Run()
}

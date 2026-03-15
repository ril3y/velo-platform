package assets

import (
	_ "embed"
	"os"
	"path/filepath"

	"github.com/ril3y/freewheel-jailbreak/internal/update"
)

//go:embed logo.png
var EmbeddedLogo []byte

// FindAPK returns a path to the APK by name.
// Search order: local file overrides (next to exe, cwd, assets/), then cached APKs
// downloaded from GitHub Releases.
func FindAPK(name string) string {
	candidates := []string{name + ".apk"}
	if name == "jailbreak" {
		candidates = append(candidates, "bowflex-jailbreak.apk")
	}

	exeDir := ""
	if exe, err := os.Executable(); err == nil {
		exeDir = filepath.Dir(exe)
	}

	// Check local overrides: cwd, exe dir, assets/ subdir
	for _, c := range candidates {
		for _, dir := range []string{".", exeDir, "assets", filepath.Join(exeDir, "assets")} {
			if dir == "" {
				continue
			}
			p := filepath.Join(dir, c)
			if _, err := os.Stat(p); err == nil {
				return p
			}
		}
	}

	// Fall back to cached APK from GitHub Releases
	return update.FindCachedAPK(name)
}

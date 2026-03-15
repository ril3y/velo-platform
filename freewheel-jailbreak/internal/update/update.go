package update

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"strconv"
	"strings"
)

const (
	owner = "ril3y"
	repo  = "velo-platform"
)

// Release represents the relevant fields from a GitHub release.
type Release struct {
	TagName string  `json:"tag_name"`
	Assets  []Asset `json:"assets"`
}

// Asset represents a downloadable file in a GitHub release.
type Asset struct {
	Name               string `json:"name"`
	BrowserDownloadURL string `json:"browser_download_url"`
	Size               int64  `json:"size"`
}

// CacheDir returns the local cache directory for FreeWheel.
// On Windows: %APPDATA%/freewheel/
func CacheDir() string {
	if runtime.GOOS == "windows" {
		if appdata := os.Getenv("APPDATA"); appdata != "" {
			return filepath.Join(appdata, "freewheel")
		}
	}
	home, _ := os.UserHomeDir()
	return filepath.Join(home, ".freewheel")
}

// APKCacheDir returns the APK cache subdirectory.
func APKCacheDir() string {
	return filepath.Join(CacheDir(), "apks")
}

// CachedVersion returns the release tag currently cached, or "".
func CachedVersion() string {
	data, err := os.ReadFile(filepath.Join(CacheDir(), "cached_version.txt"))
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(data))
}

// WriteCachedVersion persists the tag of the cached release.
func WriteCachedVersion(tag string) {
	dir := CacheDir()
	os.MkdirAll(dir, 0755)
	os.WriteFile(filepath.Join(dir, "cached_version.txt"), []byte(tag), 0644)
}

// FetchLatestRelease queries GitHub for the latest release.
func FetchLatestRelease() (*Release, error) {
	url := fmt.Sprintf("https://api.github.com/repos/%s/%s/releases/latest", owner, repo)
	req, _ := http.NewRequest("GET", url, nil)
	req.Header.Set("Accept", "application/vnd.github.v3+json")
	req.Header.Set("User-Agent", "FreeWheel")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("network error: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("GitHub API returned %d", resp.StatusCode)
	}

	var rel Release
	if err := json.NewDecoder(resp.Body).Decode(&rel); err != nil {
		return nil, fmt.Errorf("parse error: %w", err)
	}
	return &rel, nil
}

// APKAssets returns only the APK assets from a release.
func APKAssets(rel *Release) []Asset {
	var apks []Asset
	for _, a := range rel.Assets {
		if strings.HasSuffix(strings.ToLower(a.Name), ".apk") {
			apks = append(apks, a)
		}
	}
	return apks
}

// ExeAsset returns the freewheel.exe asset from a release, if present.
func ExeAsset(rel *Release) *Asset {
	for _, a := range rel.Assets {
		if strings.EqualFold(a.Name, "freewheel.exe") {
			return &a
		}
	}
	return nil
}

// ProgressFunc is called during download with bytes downloaded and total size.
type ProgressFunc func(downloaded, total int64)

// DownloadFile downloads a URL to a local path, calling progress on each chunk.
func DownloadFile(url, destPath string, progress ProgressFunc) error {
	os.MkdirAll(filepath.Dir(destPath), 0755)

	resp, err := http.Get(url)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		return fmt.Errorf("download returned %d", resp.StatusCode)
	}

	f, err := os.Create(destPath)
	if err != nil {
		return err
	}
	defer f.Close()

	total := resp.ContentLength
	var downloaded int64
	buf := make([]byte, 32*1024)

	for {
		n, readErr := resp.Body.Read(buf)
		if n > 0 {
			if _, writeErr := f.Write(buf[:n]); writeErr != nil {
				return writeErr
			}
			downloaded += int64(n)
			if progress != nil {
				progress(downloaded, total)
			}
		}
		if readErr == io.EOF {
			break
		}
		if readErr != nil {
			return readErr
		}
	}
	return nil
}

// FindCachedAPK returns the path to a cached APK by name, or "" if not cached.
func FindCachedAPK(name string) string {
	p := filepath.Join(APKCacheDir(), name+".apk")
	if _, err := os.Stat(p); err == nil {
		return p
	}
	return ""
}

// IsNewer returns true if remote version is newer than local (semver comparison).
func IsNewer(remote, local string) bool {
	remote = strings.TrimPrefix(remote, "v")
	local = strings.TrimPrefix(local, "v")

	// Strip anything after semver (e.g., "-3-gabcdef" from git describe)
	re := regexp.MustCompile(`^(\d+)\.(\d+)\.(\d+)`)

	rm := re.FindStringSubmatch(remote)
	lm := re.FindStringSubmatch(local)
	if rm == nil || lm == nil {
		return false
	}

	for i := 1; i <= 3; i++ {
		r, _ := strconv.Atoi(rm[i])
		l, _ := strconv.Atoi(lm[i])
		if r > l {
			return true
		}
		if r < l {
			return false
		}
	}
	return false
}

// APKNameFromAsset maps a release asset filename to the APK name used by FindAPK.
// e.g., "velolauncher.apk" → "velolauncher", "serialbridge.apk" → "serialbridge"
func APKNameFromAsset(filename string) string {
	return strings.TrimSuffix(strings.ToLower(filename), ".apk")
}

"""Cheap, host-only checks before building the Android APK."""

from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"


class HowardTVContractTests(unittest.TestCase):
    def test_manifest_is_branded_and_safe_for_shield(self):
        manifest = ET.parse(ROOT / "AndroidManifest.xml").getroot()
        self.assertEqual("uk.co.howardtv", manifest.attrib["package"])
        sdk = manifest.find("uses-sdk")
        self.assertLessEqual(int(sdk.attrib[ANDROID + "minSdkVersion"]), 30)
        permissions = {
            tag.attrib[ANDROID + "name"]
            for tag in manifest.findall("uses-permission")
        }
        self.assertNotIn("android.permission.READ_LOGS", permissions)
        self.assertNotIn("android.permission.ACCESS_FINE_LOCATION", permissions)
        app = manifest.find("application")
        self.assertEqual("@drawable/howardtv_badge", app.attrib[ANDROID + "icon"])
        self.assertEqual("@drawable/howardtv_banner", app.attrib[ANDROID + "banner"])
        home = next(a for a in app.findall("activity") if a.attrib[ANDROID + "name"] == ".HomeActivity")
        self.assertEqual("true", home.attrib[ANDROID + "exported"])
        categories = {
            e.attrib[ANDROID + "name"]
            for f in home.findall("intent-filter")
            for e in f.findall("category")
        }
        self.assertIn("android.intent.category.HOME", categories)
        self.assertIn("android.intent.category.LEANBACK_LAUNCHER", categories)

    def test_branding_and_original_wallpaper_are_bundled(self):
        self.assertTrue((ROOT / "assets" / "cinema_wallpaper.jpg").is_file())
        self.assertTrue((ROOT / "assets" / "HowardTV-logo.png").is_file())
        self.assertTrue((ROOT / "res" / "drawable-nodpi" / "howardtv_badge.png").is_file())
        self.assertTrue((ROOT / "res" / "drawable-nodpi" / "howardtv_banner.png").is_file())
        self.assertIn("HowardTV", (ROOT / "res" / "values" / "strings.xml").read_text())

    def test_dock_and_status_layout(self):
        source = (ROOT / "src" / "uk" / "co" / "howardtv" / "HomeActivity.java").read_text()
        self.assertIn("DOCK_SIZE = 8", source)
        self.assertIn("All Applications", source)
        layout = ET.parse(ROOT / "res" / "layout" / "activity_home.xml").getroot()
        image = next(e for e in layout.iter() if e.attrib.get(ANDROID + "id") == "@+id/howardtv_logo")
        self.assertIsNotNone(image)
        clock = next(e for e in layout.iter() if e.attrib.get(ANDROID + "id") == "@+id/clock")
        self.assertEqual("end", clock.attrib.get(ANDROID + "gravity"))


if __name__ == "__main__":
    unittest.main()

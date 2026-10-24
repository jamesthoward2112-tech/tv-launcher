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
        self.assertGreaterEqual(int(manifest.attrib[ANDROID + "versionCode"]), 2)
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

    def test_phone_preview_fits_tv_canvas_without_changing_shield_layout(self):
        layout = ET.parse(ROOT / "res" / "layout" / "activity_home.xml").getroot()
        preview = next(e for e in layout.iter() if e.attrib.get(ANDROID + "id") == "@+id/preview_canvas")
        self.assertTrue(any(e.attrib.get(ANDROID + "id") == "@+id/content" for e in preview.iter()))
        logo = next(e for e in preview.iter() if e.attrib.get(ANDROID + "id") == "@+id/howardtv_logo")
        self.assertLessEqual(int(logo.attrib[ANDROID + "layout_width"].removesuffix("dp")), 145)
        self.assertLessEqual(int(logo.attrib[ANDROID + "layout_height"].removesuffix("dp")), 48)

        home = (ROOT / "src" / "uk" / "co" / "howardtv" / "HomeActivity.java").read_text()
        self.assertIn("configurePhonePreview()", home)
        self.assertIn("Configuration.UI_MODE_TYPE_TELEVISION", home)
        self.assertIn("setScaleX(scale)", home)
        self.assertIn("setScaleY(scale)", home)
        self.assertIn("Math.min(", home)

    def test_phone_can_open_launcher_from_app_drawer(self):
        manifest = ET.parse(ROOT / "AndroidManifest.xml").getroot()
        home = next(a for a in manifest.find("application").findall("activity") if a.attrib[ANDROID + "name"] == ".HomeActivity")
        filters = [
            {e.attrib[ANDROID + "name"] for e in f.findall("category")}
            for f in home.findall("intent-filter")
        ]
        self.assertTrue(any("android.intent.category.LAUNCHER" in categories for categories in filters))
        self.assertTrue(any("android.intent.category.HOME" in categories for categories in filters))
        self.assertGreaterEqual(int(manifest.attrib[ANDROID + "versionCode"]), 3)

    def test_requested_fonts_are_used_in_the_right_places(self):
        fonts = ROOT / "assets" / "fonts"
        self.assertTrue((fonts / "BebasNeue-Regular.ttf").is_file())
        self.assertTrue((fonts / "LEMONMILK-Regular.otf").is_file())
        font_loader = (ROOT / "src" / "uk" / "co" / "howardtv" / "Fonts.java").read_text()
        home = (ROOT / "src" / "uk" / "co" / "howardtv" / "HomeActivity.java").read_text()
        tiles = (ROOT / "src" / "uk" / "co" / "howardtv" / "TileAdapter.java").read_text()
        self.assertIn("static Typeface bebas(", font_loader)
        self.assertIn("static Typeface lemon(", font_loader)
        self.assertIn("Fonts.bebas(getAssets())", home)
        self.assertIn("Fonts.lemon(getAssets())", home)
        self.assertIn("Fonts.lemon(", tiles)

    def test_uniform_logo_only_dock_and_iptv_mapping(self):
        app = (ROOT / "src" / "uk" / "co" / "howardtv" / "App.java").read_text()
        home = (ROOT / "src" / "uk" / "co" / "howardtv" / "HomeActivity.java").read_text()
        tiles = (ROOT / "src" / "uk" / "co" / "howardtv" / "TileAdapter.java").read_text()
        self.assertTrue((ROOT / "src" / "uk" / "co" / "howardtv" / "BrandTile.java").is_file())
        self.assertIn("BrandTile.create(", app)
        self.assertIn('"IPTV"', home)
        self.assertIn("ImageView.ScaleType.FIT_CENTER", tiles)
        self.assertNotIn("ImageView.ScaleType.CENTER_CROP", tiles)
        self.assertIn("h.name.setVisibility(View.GONE)", tiles)

    def test_home_launcher_can_be_selected_without_disabling_stock_home(self):
        installer = (ROOT / "install.sh").read_text()
        self.assertIn("cmd package set-home-activity", installer)
        self.assertNotIn("pm disable-user", installer)


if __name__ == "__main__":
    unittest.main()

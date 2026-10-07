import unittest
from validate_release import validate, validate_docs


class ReleaseValidationTest(unittest.TestCase):
    def test_released_installation_coordinates(self):
        validate_docs("0.3.0", {"README": 'platform("io.github.junelegency:ai-elements-bom:0.3.0")'})

    def test_rejects_stale_installation_docs(self):
        for text in (
            'platform("io.github.junelegency:ai-elements-bom:0.3.0-SNAPSHOT")',
            'platform("io.github.junelegency:ai-elements-bom:0.3.0")\n0.3.0 is not released yet',
            'platform("io.github.junelegency:ai-elements-bom:0.3.01")',
        ):
            with self.subTest(text=text), self.assertRaises(ValueError):
                validate_docs("0.3.0", {"README": text})

    def test_final_version(self):
        self.assertEqual("0.3.0", validate("v0.3.0", "VERSION_NAME=0.3.0\n", "## 0.3.0\nRelease notes\n"))

    def test_rejects_unreleased_prefixes_and_empty_notes(self):
        for notes in ("## 0.3.0 (unreleased)\nNotes", "## 0.3.01\nNotes", "## 0.3.0\n\n", "## 0.3.0\nA\n## 0.3.0\nB"):
            with self.subTest(notes=notes), self.assertRaises(ValueError):
                validate("v0.3.0", "VERSION_NAME=0.3.0", notes)

    def test_rejects_invalid_tags_and_version_mismatch(self):
        for tag, version in (("v0.3.0", "0.3.0-SNAPSHOT"), ("v0.3.0-rc1", "0.3.0-rc1"), ("v0x3x0", "0x3x0"), ("v00.3.0", "00.3.0")):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                validate(tag, "VERSION_NAME=" + version, "## " + version + "\nNotes")


if __name__ == "__main__":
    unittest.main()

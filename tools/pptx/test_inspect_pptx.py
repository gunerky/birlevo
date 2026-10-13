import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

from inspect_pptx import A, P, R, REL, Rejected, inspect


def package():
    return {
        '[Content_Types].xml': '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"/>',
        '_rels/.rels': f'<Relationships xmlns="{REL}"><Relationship Id="Main" Type="{R}/officeDocument" Target="ppt/presentation.xml"/></Relationships>',
        'ppt/presentation.xml': f'<p:presentation xmlns:p="{P}" xmlns:r="{R}"><p:sldIdLst><p:sldId id="256" r:id="Second"/><p:sldId id="257" r:id="First"/></p:sldIdLst><p:sldSz cx="9144000" cy="5143500"/></p:presentation>',
        'ppt/_rels/presentation.xml.rels': f'<Relationships xmlns="{REL}"><Relationship Id="First" Type="{R}/slide" Target="slides/slide1.xml"/><Relationship Id="Second" Type="{R}/slide" Target="slides/slide2.xml"/></Relationships>',
        'ppt/slides/slide1.xml': f'<p:sld xmlns:p="{P}" xmlns:a="{A}"><p:cSld><a:p><a:r><a:t>First</a:t></a:r></a:p></p:cSld></p:sld>',
        'ppt/slides/slide2.xml': f'<p:sld xmlns:p="{P}" xmlns:a="{A}"><p:cSld><a:p><a:r><a:t>İstanbul &amp; العربية 日本語</a:t></a:r><a:br/><a:r><a:t>second line</a:t></a:r></a:p></p:cSld></p:sld>',
        'ppt/slides/_rels/slide2.xml.rels': f'<Relationships xmlns="{REL}"><Relationship Id="Note" Type="{R}/notesSlide" Target="../notesSlides/notesSlide2.xml"/></Relationships>',
        'ppt/notesSlides/notesSlide2.xml': f'<p:notes xmlns:p="{P}" xmlns:a="{A}"><p:sp><p:ph type="body"/><a:p><a:r><a:t>PRIVATE NOTE</a:t></a:r></a:p></p:sp><p:sp><p:ph type="sldNum"/><a:p><a:r><a:t>99</a:t></a:r></a:p></p:sp></p:notes>',
    }


class InventoryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.file = Path(self.temp.name, 'test.pptx')

    def write(self, parts):
        with ZipFile(self.file, 'w', ZIP_DEFLATED) as archive:
            for name, text in parts.items():
                archive.writestr(name, text)
        return self.file

    def reject(self, parts):
        with self.assertRaises(Rejected):
            inspect(self.write(parts))

    def test_relationship_order_unicode_breaks_and_private_notes(self):
        result = inspect(self.write(package()))
        self.assertEqual(result['slide_count'], 2)
        self.assertEqual(result['slides'][0]['part'], 'ppt/slides/slide2.xml')
        self.assertEqual(result['slides'][0]['text'], 'İstanbul & العربية 日本語\nsecond line')
        self.assertEqual(result['slides'][0]['notes'], 'PRIVATE NOTE')
        self.assertNotIn('PRIVATE', result['slides'][0]['text'])
        self.assertFalse(result['general_import_ready'])

    def test_external_content_is_not_fetched_and_requires_warning(self):
        parts = package()
        parts['ppt/slides/_rels/slide1.xml.rels'] = f'<Relationships xmlns="{REL}"><Relationship Id="Web" Type="{R}/image" Target="https://invalid.example/not-fetched.png" TargetMode="External"/></Relationships>'
        parts['ppt/slides/slide1.xml'] = f'<p:sld xmlns:p="{P}" xmlns:a="{A}" xmlns:r="{R}"><a:blip r:link="Web"/></p:sld>'
        result = inspect(self.write(parts))
        self.assertEqual(len(result['external_relationships']), 1)
        self.assertIn('external-content-not-loaded', result['slides'][1]['issues'])

    def test_complex_features_are_reported_instead_of_silently_mapped(self):
        parts = package()
        parts['ppt/slides/slide1.xml'] = f'<p:sld xmlns:p="{P}" xmlns:a="{A}" xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart"><p:timing/><p:transition/><p:grpSp/><a:tbl/><c:chart/><a:srcRect l="20000"/><a:xfrm rot="5400000"/></p:sld>'
        issues = inspect(self.write(parts))['slides'][1]['issues']
        self.assertTrue({'table', 'chart', 'animation', 'transition', 'group', 'image-crop', 'transform'}.issubset(issues))

    def test_dtd_and_entity_definitions_rejected(self):
        parts = package(); parts['ppt/slides/slide1.xml'] = '<!DOCTYPE slide [<!ENTITY a "boom">]><slide>&a;</slide>'
        self.reject(parts)

    def test_utf16_is_explicitly_outside_trial_scope(self):
        parts = package(); parts['ppt/slides/slide1.xml'] = parts['ppt/slides/slide1.xml'].encode('utf-16')
        self.reject(parts)

    def test_xml_bomb_and_depth_are_bounded(self):
        parts = package(); parts['ppt/slides/slide1.xml'] = '<s>' + ('x' * (2 * 1024 * 1024)) + '</s>'
        self.reject(parts)
        parts['ppt/slides/slide1.xml'] = ('<s>' * 102) + ('</s>' * 102)
        self.reject(parts)

    def test_archive_traversal_rejected_without_writing_to_disk(self):
        parts = package(); parts['../../escape.xml'] = '<x/>'; self.reject(parts)

    def test_relationship_traversal_and_missing_target_rejected(self):
        for target in ('../../../escape.xml', '../missing.xml', '%2e%2e/%2e%2e/%2e%2e/escape.xml', 'https://invalid.example/part'):
            parts = package(); parts['ppt/_rels/presentation.xml.rels'] = parts['ppt/_rels/presentation.xml.rels'].replace('slides/slide1.xml', target)
            self.reject(parts)

    def test_duplicate_parts_rejected(self):
        self.write(package())
        import warnings
        with warnings.catch_warnings():
            warnings.simplefilter('ignore')
            with ZipFile(self.file, 'a') as archive: archive.writestr('ppt/slides/slide1.xml', '<x/>')
        with self.assertRaises(Rejected): inspect(self.file)

    def test_macros_and_duplicate_relationships_rejected(self):
        parts = package(); parts['ppt/vbaProject.bin'] = b'not executed'; self.reject(parts)
        parts = package(); parts['_rels/.rels'] = parts['_rels/.rels'].replace('</Relationships>', f'<Relationship Id="Main" Type="{R}/officeDocument" Target="ppt/presentation.xml"/></Relationships>')
        self.reject(parts)

    def test_strict_pptx_and_broken_object_rejected(self):
        parts = package(); parts['ppt/presentation.xml'] = parts['ppt/presentation.xml'].replace(P, 'http://purl.oclc.org/ooxml/presentationml/main'); self.reject(parts)
        parts = package(); parts['ppt/slides/slide1.xml'] = f'<p:sld xmlns:p="{P}" xmlns:a="{A}" xmlns:r="{R}"><a:blip r:embed="Missing"/></p:sld>'; self.reject(parts)

    def test_unsupported_slide_count_and_duplicate_identity_rejected(self):
        parts = package(); parts['ppt/presentation.xml'] = parts['ppt/presentation.xml'].replace('id="257"', 'id="256"'); self.reject(parts)
        parts = package(); parts['ppt/presentation.xml'] = parts['ppt/presentation.xml'].replace('cx="9144000"', 'cx="0"'); self.reject(parts)


if __name__ == '__main__':
    unittest.main()

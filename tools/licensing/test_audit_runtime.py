"""Reviewed declaration recognition must not turn into blanket dependency approval."""
import unittest
from audit_runtime import identified, pom_url


class ReviewedLicenseDeclarations(unittest.TestCase):
    def test_reviewed_checker_qual_mit_declaration(self):
        self.assertEqual(['MIT'], identified([{'name': 'The MIT License'}]))

    def test_existing_declarations_remain_distinct(self):
        self.assertEqual(['Apache-2.0', 'Bouncy-Castle'], identified([
            {'name': 'The Apache Software License, Version 2.0'},
            {'name': 'Bouncy Castle Licence'},
        ]))

    def test_unreviewed_licenses_and_lookalike_names_still_fail(self):
        for name in ['GNU General Public License, Version 3', 'MIT-like custom terms', 'Unknown']:
            with self.subTest(name=name), self.assertRaises(ValueError):
                identified([{'name': name}])

    def test_google_sdk_license_requires_exact_reviewed_coordinate_and_url(self):
        declaration = {'name': 'Android Software Development Kit License',
                       'url': 'https://developer.android.com/studio/terms.html'}
        coordinate = 'com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.5.0'
        self.assertEqual(['LicenseRef-Google-Android-SDK'], identified([declaration], coordinate))
        for changed in [None, coordinate.replace('1.5.0', '1.6.0'), 'unreviewed:package:1.0']:
            with self.subTest(coordinate=changed), self.assertRaises(ValueError):
                identified([declaration], changed)
        with self.assertRaises(ValueError):
            identified([dict(declaration, url='https://example.com')], coordinate)

    def test_google_groups_use_official_google_maven(self):
        for coordinate in ['com.google.android.ump:user-messaging-platform:4.0.0',
                           'com.google.android.gms:play-services-appset:16.0.1']:
            self.assertTrue(pom_url(coordinate).startswith('https://dl.google.com/'))
        self.assertTrue(pom_url('com.google.protobuf:protobuf-javalite:3.22.3')
                        .startswith('https://repo.maven.apache.org/'))


if __name__ == '__main__':
    unittest.main()

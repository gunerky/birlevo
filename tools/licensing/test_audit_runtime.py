"""Reviewed declaration recognition must not turn into blanket dependency approval."""
import unittest
from audit_runtime import identified


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


if __name__ == '__main__':
    unittest.main()

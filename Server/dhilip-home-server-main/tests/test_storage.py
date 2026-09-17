"""
DhilipHome Server - Storage Unit Tests
"""

import unittest
from app import create_app


class TestStorageEndpoints(unittest.TestCase):
    """Test suite for storage and filesystem monitoring APIs."""

    def setUp(self):
        self.app = create_app()
        self.client = self.app.test_client()

    def test_storage_endpoint(self):
        response = self.client.get("/api/storage")
        self.assertEqual(response.status_code, 200)
        data = response.get_json()

        self.assertIn("drives", data)
        self.assertIsInstance(data["drives"], list)
        self.assertGreaterEqual(len(data["drives"]), 1)

        drive = data["drives"][0]
        self.assertIn("mount", drive)
        self.assertIn("total_bytes", drive)
        self.assertIn("used_bytes", drive)
        self.assertIn("free_bytes", drive)
        self.assertIn("usage_percent", drive)
        self.assertGreater(drive["total_bytes"], 0)


if __name__ == "__main__":
    unittest.main()

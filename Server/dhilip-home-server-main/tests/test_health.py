"""
DhilipHome Server - Health and Discovery Unit Tests
"""

import unittest
from app import create_app


class TestHealthEndpoints(unittest.TestCase):
    """Test suite for health, server identity, and LAN discovery APIs."""

    def setUp(self):
        self.app = create_app()
        self.client = self.app.test_client()

    def test_health_check(self):
        response = self.client.get("/api/health")
        self.assertEqual(response.status_code, 200)
        data = response.get_json()
        self.assertEqual(data.get("status"), "ok")
        self.assertEqual(data.get("server"), "DhilipHome Server")
        self.assertEqual(data.get("version"), "0.3.3")
        self.assertIn("timestamp", data)
        self.assertIn("uptime_seconds", data)
        self.assertIsInstance(data["uptime_seconds"], int)

    def test_server_info(self):
        response = self.client.get("/api/server")
        self.assertEqual(response.status_code, 200)
        data = response.get_json()
        self.assertEqual(data.get("name"), "DhilipHome Server")
        self.assertEqual(data.get("version"), "0.3.3")
        self.assertIn("hostname", data)
        self.assertIn("ip", data)
        self.assertIn("port", data)
        self.assertIn("os", data)
        self.assertIn("python_version", data)
        self.assertIn("uptime_seconds", data)

    def test_discovery_endpoint(self):
        response = self.client.get("/api/discovery")
        self.assertEqual(response.status_code, 200)
        data = response.get_json()
        self.assertEqual(data.get("service"), "DhilipHome Server")
        self.assertEqual(data.get("version"), "0.3.3")
        self.assertIn("port", data)
        self.assertEqual(data.get("api"), "/api")
        self.assertIn("device_name", data)


if __name__ == "__main__":
    unittest.main()

"""
DhilipHome Server - System Monitor Unit Tests
"""

import unittest
from app import create_app


class TestSystemEndpoints(unittest.TestCase):
    """Test suite for system hardware monitoring APIs."""

    def setUp(self):
        self.app = create_app()
        self.client = self.app.test_client()

    def test_complete_system_metrics(self):
        response = self.client.get("/api/system")
        self.assertEqual(response.status_code, 200)
        data = response.get_json()

        # Verify required root metric sections
        self.assertIn("cpu", data)
        self.assertIn("memory", data)
        self.assertIn("swap", data)
        self.assertIn("uptime", data)
        self.assertIn("operating_system", data)
        self.assertIn("processes", data)

        # CPU verification
        cpu = data["cpu"]
        self.assertIn("usage_percent", cpu)
        self.assertIn("physical_cores", cpu)
        self.assertIn("logical_cores", cpu)
        self.assertGreaterEqual(cpu["logical_cores"], 1)

        # RAM verification
        mem = data["memory"]
        self.assertIn("total_bytes", mem)
        self.assertIn("used_bytes", mem)
        self.assertIn("available_bytes", mem)
        self.assertIn("usage_percent", mem)
        self.assertGreater(mem["total_bytes"], 0)

        # Uptime verification
        uptime = data["uptime"]
        self.assertIn("system_uptime_seconds", uptime)
        self.assertIn("server_uptime_seconds", uptime)

    def test_cpu_sub_endpoint(self):
        response = self.client.get("/api/system/cpu")
        self.assertEqual(response.status_code, 200)
        data = response.get_json()
        self.assertIn("usage_percent", data)
        self.assertIn("physical_cores", data)

    def test_memory_sub_endpoint(self):
        response = self.client.get("/api/system/memory")
        self.assertEqual(response.status_code, 200)
        data = response.get_json()
        self.assertIn("memory", data)
        self.assertIn("swap", data)


if __name__ == "__main__":
    unittest.main()

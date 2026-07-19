import sys
import unittest
from pathlib import Path

# Add Web_dashboard to path
web_dashboard_dir = Path(__file__).resolve().parent.parent.parent.parent.parent
if str(web_dashboard_dir) not in sys.path:
    sys.path.insert(0, str(web_dashboard_dir))

from backend.app.services.severity import compute_severity, SeverityResult
from backend.app.services.forecast import calculate_forecast

class TestSeverityAndForecast(unittest.TestCase):
    def setUp(self):
        self.calibration = {
            "baseline_temperature": 30.0,
            "baseline_frequency": 8.0,
            "severity_thresholds": { "normal": 25, "minor": 50, "major": 75 }
        }

    def test_severity_normal(self):
        res = compute_severity(
            reconstruction_error=0.1,
            temperature=30.0,
            frequency=8.0,
            health_trend=0.0,
            calibration=self.calibration
        )
        self.assertEqual(res.level, "Normal")
        self.assertLess(res.score, 25)

    def test_severity_critical(self):
        res = compute_severity(
            reconstruction_error=0.9,    # error_factor = 0.9 (weight 40 -> 36)
            temperature=50.0,             # temp_factor = abs(50-30)/20 = 1.0 (weight 20 -> 20)
            frequency=4.0,                # freq_factor = abs(4-8)/4 = 1.0 (weight 20 -> 20)
            health_trend=-6.0,            # trend_factor = max(6,0)/5 = 1.0 (weight 20 -> 20)
            calibration=self.calibration
        )
        self.assertEqual(res.level, "Critical")
        self.assertEqual(res.score, 96.0)

    def test_forecast_values(self):
        try:
            from backend.app.database.database import init_db
            init_db()
            res = calculate_forecast("NODE_01", 0.5)
            self.assertIn("eta_days", res)
            self.assertIn("predicted_health", res)
            self.assertIn("trend", res)
            print("Forecast test output:", res)
        except Exception as e:
            self.fail(f"Forecast test failed: {e}")

if __name__ == "__main__":
    unittest.main()

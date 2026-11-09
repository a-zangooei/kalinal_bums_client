import os
import glob
import xml.etree.ElementTree as ET

def parse_and_report():
    xml_files = sorted(glob.glob('app/build/test-results/testDebugUnitTest/*.xml'))
    total_tests = 0
    total_failures = 0
    total_errors = 0
    total_skipped = 0
    total_time = 0.0
    suite_details = []

    for file_path in xml_files:
        try:
            tree = ET.parse(file_path)
            root = tree.getroot()
            t = int(root.attrib.get('tests', 0))
            f = int(root.attrib.get('failures', 0))
            e = int(root.attrib.get('errors', 0))
            s = int(root.attrib.get('skipped', 0))
            tm = float(root.attrib.get('time', 0.0))
            raw_name = root.attrib.get('name', os.path.basename(file_path))
            short_name = raw_name.split('.')[-1]

            total_tests += t
            total_failures += f
            total_errors += e
            total_skipped += s
            total_time += tm

            status = "✅ پاس شد" if (f == 0 and e == 0) else "❌ خطا"
            suite_details.append(f"| `{short_name}` | {t} | {t - f - e - s} | {f + e} | {s} | {tm:.2f}s | {status} |")
        except Exception as err:
            print(f"Error parsing {file_path}: {err}")

    success_count = total_tests - total_failures - total_errors - total_skipped
    success_rate = (success_count / total_tests * 100) if total_tests > 0 else 0

    summary_path = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary_path:
        with open(summary_path, 'a', encoding='utf-8') as out:
            out.write("## 🧪 نتایج پویا و خودکار تست‌های واحد (Unit Tests Summary)\n\n")
            out.write(f"- **کل آزمون‌ها:** `{total_tests}` مورد\n")
            out.write(f"- **موفق (Passed):** `{success_count}` مورد (✅ {success_rate:.1f}%)\n")
            out.write(f"- **ناموفق (Failed):** `{total_failures + total_errors}` مورد\n")
            out.write(f"- **نادیده‌گرفته‌شده (Skipped):** `{total_skipped}` مورد\n")
            out.write(f"- **زمان کل اجرا:** `{total_time:.2f}` ثانیه\n\n")

            out.write("| کلاس آزمون (Test Suite) | کل تست‌ها | موفق | ناموفق | ردشده | مدت‌زمان | وضعیت |\n")
            out.write("|---|:---:|:---:|:---:|:---:|:---:|:---:|\n")
            for row in suite_details:
                out.write(row + "\n")
            out.write("\n")

if __name__ == '__main__':
    parse_and_report()

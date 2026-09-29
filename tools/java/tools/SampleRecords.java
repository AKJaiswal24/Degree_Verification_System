package tools;

import java.util.ArrayList;
import java.util.List;

public class SampleRecords {

    public static final String[] IDS = {
            "CER1IBAU0001",
            "CER1IBAU0002",
            "CER1IBAU0003",
            "CER2IBAU0004",
            "CER2IBAU0005",
            "CER3IBAU0006",
            "CER3IBAU0007"
    };

    public static final String[] NAMES = {
            "Aarav Sharma",
            "Diya Patel",
            "Rohan Verma",
            "Ananya Iyer",
            "Kabir Singh",
            "Meera Nair",
            "Arjun Rao"
    };

    public static final String[] COURSES = {
            "B.Tech Computer Science",
            "B.Tech Computer Science",
            "B.Tech Electronics",
            "B.Tech Mechanical",
            "Bachelor of Arts",
            "Bachelor of Science",
            "Bachelor of Commerce"
    };

    public static final String[] YEARS = {"2026", "2026", "2026", "2025", "2025", "2024", "2024"};

    public static final String[] PERCENTAGES = {
            "88.40", "91.20", "74.75", "82.10", "69.30", "77.85", "65.90"
    };

    public static final String[] PLAIN_FIXTURES = {
            "CER1IBAU0001",
            "https://example.edu/verify?id=7",
            "WIFI:S=CampusNet;T=WPA;P=secret;;"
    };

    public static final String[] SECURE_FIXTURES = {
            "SQR1AAAABBBBCCCCDDDDEEEE"
    };

    public static List<String> allIds() {
        List<String> list = new ArrayList<>();
        for (String id : IDS) {
            list.add(id);
        }
        return list;
    }
}

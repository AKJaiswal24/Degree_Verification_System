package com.example.ibu_verification.db;

import com.example.ibu_verification.model.Certificate;
import com.example.ibu_verification.security.EncryptionHelper;

public class CertificateSeeder {

    private final CertificateRepository repository;

    public CertificateSeeder(CertificateRepository repository) {
        this.repository = repository;
    }

    public void seedIfEmpty() throws EncryptionHelper.EncryptionException {
        if (!repository.isEmpty()) {
            return;
        }
        insert("CER1IBAU0001", "Aarav Sharma", "IBU2026CS001", "ENR20260001",
                "B.Tech Computer Science", "iB University", 2026, "88.40", "Degree");
        insert("CER1IBAU0002", "Diya Patel", "IBU2026CS002", "ENR20260002",
                "B.Tech Computer Science", "iB University", 2026, "91.20", "Degree");
        insert("CER1IBAU0003", "Rohan Verma", "IBU2026EC003", "ENR20260003",
                "B.Tech Electronics", "iB University", 2026, "74.75", "Degree");
        insert("CER2IBAU0004", "Ananya Iyer", "IBU2025ME004", "ENR20250004",
                "B.Tech Mechanical", "iB University", 2025, "82.10", "Degree");
        insert("CER2IBAU0005", "Kabir Singh", "IBU2025BA005", "ENR20250005",
                "Bachelor of Arts", "iB University", 2025, "69.30", "Degree");
        insert("CER3IBAU0006", "Meera Nair", "IBU2024BS006", "ENR20240006",
                "Bachelor of Science", "iB University", 2024, "77.85", "Degree");
        insert("CER3IBAU0007", "Arjun Rao", "IBU2024BC007", "ENR20240007",
                "Bachelor of Commerce", "iB University", 2024, "65.90", "Degree");
    }

    private void insert(String certificateId, String name, String roll, String enrollment,
                        String course, String university, int year, String percentage, String type)
            throws EncryptionHelper.EncryptionException {
        Certificate certificate = new Certificate();
        certificate.setCertificateId(certificateId);
        certificate.setStudentName(name);
        certificate.setRollNumber(roll);
        certificate.setEnrollmentNumber(enrollment);
        certificate.setCourseName(course);
        certificate.setUniversityName(university);
        certificate.setPassingYear(year);
        certificate.setPercentage(percentage);
        certificate.setCertificateType(type);
        repository.insert(certificate);
    }
}

package cz.uhk.ordinace.patient.service;

import cz.uhk.ordinace.common.BusinessRuleException;

final class PhoneNumbers {

    private PhoneNumbers() {
    }

    /** Ponechá jen číslice a odstraní českou předvolbu (+420 / 00420). */
    static String normalize(String raw) {
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        if (digits.length() == 12 && digits.startsWith("420")) {
            digits = digits.substring(3);
        }
        if (digits.isEmpty()) {
            throw new BusinessRuleException("Phone number must contain digits");
        }
        return digits;
    }
}

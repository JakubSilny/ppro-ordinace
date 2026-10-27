package cz.uhk.ordinace.patient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import cz.uhk.ordinace.common.BusinessRuleException;

class PhoneNumbersTest {

    @Test
    void stripsSpacesAndCzechPrefix() {
        assertThat(PhoneNumbers.normalize("+420 602 111 222")).isEqualTo("602111222");
        assertThat(PhoneNumbers.normalize("00420602111222")).isEqualTo("602111222");
        assertThat(PhoneNumbers.normalize("602-111-222")).isEqualTo("602111222");
        assertThat(PhoneNumbers.normalize("+420 602 111")).isEqualTo("602111");
    }

    @Test
    void rejectsInputWithoutDigits() {
        assertThatThrownBy(() -> PhoneNumbers.normalize("abc")).isInstanceOf(BusinessRuleException.class);
    }
}

package com.example.bank.service;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class AccountNumberGeneratorTest {

    @Test
    void generatedNumberHasUkrainianIbanFormat() {
        String number = AccountNumberGenerator.generate();

        assertThat(number).hasSize(29).matches("UA\\d{27}");
    }

    @Test
    void generatedNumberHasValidChecksum() {
        for (int i = 0; i < 1000; i++) {
            assertThat(AccountNumberGenerator.isValid(AccountNumberGenerator.generate())).isTrue();
        }
    }

    @Test
    void checksumDetectsMistakes() {
        assertThat(AccountNumberGenerator.isValid("GB82WEST12345698765432")).isTrue();
        assertThat(AccountNumberGenerator.isValid("GB82WEST12345698765433")).isFalse();
        assertThat(AccountNumberGenerator.isValid(null)).isFalse();
    }

    @Test
    void numbersDoNotRepeat() {
        Set<String> numbers = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            numbers.add(AccountNumberGenerator.generate());
        }
        assertThat(numbers).hasSize(1000);
    }
}
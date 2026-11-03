package com.example.bank.service;
import java.security.SecureRandom;
public final class AccountNumberGenerator {
    private static final String COUNTRY = "UA";
    private static final String BANK_CODE = "999999";
    private static final int ACCOUNT_DIGITS = 19;
    private static final SecureRandom RANDOM = new SecureRandom();

    public AccountNumberGenerator(){}

    public static String generate(){
        StringBuilder bban = new StringBuilder(BANK_CODE);
        for(int i = 0;i<ACCOUNT_DIGITS;i++){
            bban.append(RANDOM.nextInt(10));
        }
        int checkDigits = 98 - mod97(bban+COUNTRY+"00");
        return COUNTRY + String.format("%02d", checkDigits)+bban;
    }
    private static int mod97(String text){
        int remainder = 0;
        for(char ch:text.toCharArray()){
            int value = Character.getNumericValue(ch);
            remainder = (value<10 ? remainder*10+value : remainder*100+value) % 97;
        }
        return remainder;
    }
    public static boolean isValid(String iban){
        if(iban == null || !iban.matches("[A-Z]{2}\\d{2}[A-Z0-9]{11,30}")){
            return false;
        }
        return mod97(iban.substring(4) + iban.substring(0, 4)) == 1;
    }
}

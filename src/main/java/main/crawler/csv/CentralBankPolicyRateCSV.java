package main.crawler.csv;

import lombok.Data;

@Data
public class CentralBankPolicyRateCSV {

    private String country;
    private String currency;
    private String unit;
    private Double value;
    private String date;

}



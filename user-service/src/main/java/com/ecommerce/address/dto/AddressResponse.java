package com.ecommerce.address.dto;

import com.ecommerce.address.Address;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AddressResponse {
    private Long id;
    private String street;
    private String city;
    private String state;
    private String pincode;
    private String country;
    private boolean isDefault;

    public static AddressResponse from(Address a) {
        return AddressResponse.builder()
                .id(a.getId())
                .street(a.getStreet())
                .city(a.getCity())
                .state(a.getState())
                .pincode(a.getPincode())
                .country(a.getCountry())
                .isDefault(a.isDefault())
                .build();
    }
}

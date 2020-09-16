package com.pekochu.lynx.utilities;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GoogleDriveServiceImpl implements GoogleDriveService{
    @Value("${env.google.client}")
    private String clientId;

    @Value("${env.google.secret}")
    private String clientSecret;

    public String getCliendId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }
}

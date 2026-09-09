package com.mtfm.gateway.capability.hikvision;

import com.mtfm.gateway.spi.probe.DiscoveredDevice;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HikvisionDeviceListParserTest {

    @Test
    void parseKeepsAccessControlAndOnlineFromActiveStatus() {
        String json = """
                {
                  "SearchResult": {
                    "totalMatches": 3,
                    "MatchList": [
                      {
                        "Device": {
                          "devIndex": "1",
                          "devName": "门禁A",
                          "devType": "AccessControl",
                          "devStatus": "offline",
                          "activeStatus": true,
                          "EhomeParams": { "EhomeID": "EHOME-A" }
                        }
                      },
                      {
                        "Device": {
                          "devIndex": "2",
                          "devName": "摄像头",
                          "devType": "Camera",
                          "devStatus": "online",
                          "EhomeParams": { "EhomeID": "CAM-1" }
                        }
                      },
                      {
                        "Device": {
                          "devIndex": "3",
                          "devName": "门禁B",
                          "devType": "AccessControl",
                          "devStatus": "offline",
                          "EhomeParams": { "EhomeID": "EHOME-B" }
                        }
                      }
                    ]
                  }
                }
                """;
        HikvisionDeviceListParser.Page page = HikvisionDeviceListParser.parse(json);
        assertEquals(3, page.totalMatches());
        assertEquals(2, page.devices().size());
        DiscoveredDevice first = page.devices().get(0);
        assertEquals("EHOME-A", first.deviceCode());
        assertEquals("门禁A", first.name());
        assertEquals("1", first.address().get("deviceSerialNo").orElseThrow().toString());
        assertTrue(first.online());
        assertEquals("EHOME-B", page.devices().get(1).deviceCode());
        assertFalse(page.devices().get(1).online());
    }

    @Test
    void searchBodyFiltersAccessControlAndEhomeV5() {
        String body = HikvisionDeviceListParser.searchBody(100, 100);
        assertTrue(body.contains("\"position\":100"));
        assertTrue(body.contains("AccessControl"));
        assertTrue(body.contains("ehomeV5"));
    }
}

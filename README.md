# Central System

This is the OCPP Central System for ChargeGrid.

NOTE: Currently, Central System uses Amazon DynamoDB for storing ongoing sessions. This is going to be replaced by 
vendor neutral storage, probably PostgreSQL. You can still run Central System by using the "fake" DynamoDB docker 
container included in the ChargeGrid development environment.

## Supported protocols & operations

Central System currently supports a subset of OCPP 1.6, as described in the table below.

Central System **does NOT support OCPP1.5.** OCPP1.6 is not backwards compatible with 1.5. 
  
  
#### Operations initiated by charge-box

| Operation | Supported by CS| only in 1.6
|:-----------|:---:|--- 
| Heartbeat | Yes |
| Start Transaction | Yes |
| Stop Transaction | Yes |
| Authorize | Yes |
| Boot Notification | Yes |
| Status Notification | Yes |
| Data Transfer | - |
| Diagnostics Status Notification | - |
| Firmware Status Notification | - |
| Meter Values | - |

#### Operations initiated by central system

| Operation | Supported by CS| only in 1.6 | Endpoint
|:-----------|:---:|---|---
| Remote start transaction | Yes | - | `/evses/:evse-id/remote/start-session {token_id}`
| Remote stop transaction | Yes | - | `/evses/:evse-id/remote/stop-session {transaction_id}` 
| Unlock connector | Yes | - | `/charge-boxes/:box-id/remote/unlock-connector {connectorId}`
| Get configuration | Yes | - |  `/charge-boxes/:box-id/remote/get-configuration {}` 
| Change configuration | Yes | - |  `/charge-boxes/:box-id/remote/change-configuration {key, value}`
| Clear cache | Yes | - |  `/charge-boxes/:box-id/remote/clear-cache {}`
| Reset | Yes | - |  `/charge-boxes/:box-id/remote/reset {type:"soft/hard"}`
| Get diagnostics | Yes | - | `/charge-boxes/:box-id/remote/get-diagnostics {location}`
| Update firmware | Yes | - | `/charge-boxes/:box-id/remote/update-firmware {location, retrieveDate}`
| Cancel reservations | - | - |
| Change availability | - | - |
| Get local list version | - | - |
| Reserve now | - | - |
| Send local list | - | - |
| Clear charging profile | - | Yes |
| Get composite schedule | - | Yes |
| Send charging profile | - | Yes | 
| Trigger message | - | Yes |

## REST API

REST API to trigger supported OCPP operations:

- Endpoint: `POST /evses/:id/remote/<operation-name>`.
- The endpoint expects json body, according to the OCPP spec. 

REST API to query charge-box status:

- `GET /summary` - summary of connected evses
- `GET /sessions` - currently running or finished sessions
- `GET /connections` - currently open connections
- `GET /evses/:id` - current status of the evse and connectors
- `GET /evses/:id/sessions` - current sessions on EVSE

## RMQ

CS sends the json messages to RMQ:

- Raw OCPP messages to `ocpp` exchange
- Finished sessions to `sessions` exchange

## Local state

Local state (running sessions, etc) is stored in an external database.
 
## How to run & test

- Follow the instructions for setting up the [development environment](https://github.com/chargegrid/development-environment)
- Install `lein`
- Run `lein run`. 

Hot-reloading works on all sources except for websocket handlers. 

For testing, checkout the [Abusive Charge Point](https://github.com/chargegrid/abusive-charge-point)


## Docs & Resources

- [OCPP1.5j docs](http://www.openchargealliance.org/downloads/)

# 0.2.1

- Added callbacks:
  - onBotLLMStarted
  - onBotLLMStopped
  - onBotTTSStarted
  - onBotTTSStopped

# 0.2.0

- Renamed:
  - `VoiceClient` to `RTVIClient`
  - `VoiceClientOptions` to `RTVIClientOptions`
  - `VoiceEventCallbacks` to `RTVIEventCallbacks`
  - `VoiceError` to `RTVIError`
  - `VoiceException` to `RTVIException`
  - `VoiceClientHelper` to `RTVIClientHelper`
  - `RegisteredVoiceClient` to `RegisteredRTVIClient`
  - `FailedToFetchAuthBundle` to `HttpError`
- `RTVIClient()` constructor parameter changes
  - `options` is now mandatory
  - `baseUrl` has been moved to `options.params.baseUrl`
  - `baseUrl` and `endpoints` are now separate, and the endpoint names are appended to the `baseUrl`
- Moved `RTVIClientOptions.config` to `RTVIClientOptions.params.config`
- Moved `RTVIClientOptions.customHeaders` to `RTVIClientOptions.params.headers`
- Moved `RTVIClientOptions.customBodyParams` to `RTVIClientOptions.params.requestData`
- `TransportState` changes
  - Removed `Idle` state, replaced with `Disconnected`
  - Added `Disconnecting` state
- Added callbacks
  - `onBotLLMText()`
  - `onBotTTSText()`
  - `onStorageItemStored()`
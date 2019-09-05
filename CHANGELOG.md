# Changelog

All notable changes to this project will be documented in this file. See [standard-version](https://github.com/conventional-changelog/standard-version) for commit guidelines.

### [0.1.3](https://github.com/livepeer/livepeer-wowza/compare/0.1.2...0.1.3) (2019-08-29)


### Bug Fixes

* **wowza:** handle large segments more gracefully, default to smaller segments ([1740ebe](https://github.com/livepeer/livepeer-wowza/commit/1740ebe))

### 0.1.2 (2019-08-28)


### Bug Fixes

* **wowza:** increase hls buffer size in stream files ([b9c6cd2](https://github.com/livepeer/livepeer-wowza/commit/b9c6cd2))
* **wowza:** remove incorrect codec information ([a34896d](https://github.com/livepeer/livepeer-wowza/commit/a34896d))

### 0.1.1 (2019-08-27)


### Features

* **wowza:** add all of the requested stream name groups ([50319e5](https://github.com/livepeer/livepeer-wowza/commit/50319e5))
* **wowza:** implement authorization header ([96c5c28](https://github.com/livepeer/livepeer-wowza/commit/96c5c28))
* **wowza:** implement dynamic stream name group creation ([ab3177a](https://github.com/livepeer/livepeer-wowza/commit/ab3177a))

## 0.1.0 (2019-08-23)


### Bug Fixes

* **wowza:** automatically reinitialize streams when the broadcaster goes down ([a4aa5d8](https://github.com/livepeer/livepeer-wowza/commit/a4aa5d8))
* **wowza:** ignore transcoded streamfile renditions ([479df7a](https://github.com/livepeer/livepeer-wowza/commit/479df7a))
* **wowza:** properly obey retry and timeout parameters ([2d88481](https://github.com/livepeer/livepeer-wowza/commit/2d88481)), closes [#22](https://github.com/livepeer/livepeer-wowza/issues/22)


### Features

* **wowza:** add custom API server urls, API key parsing ([b53cafc](https://github.com/livepeer/livepeer-wowza/commit/b53cafc))
* **wowza:** allow dynamic stream URL switching ([314c111](https://github.com/livepeer/livepeer-wowza/commit/314c111))
* **wowza:** automatically disconnect streamfiles upon disconnect ([85b4e34](https://github.com/livepeer/livepeer-wowza/commit/85b4e34))
* **wowza:** implement automatic SMIL file creation ([a2c4e1d](https://github.com/livepeer/livepeer-wowza/commit/a2c4e1d)), closes [#25](https://github.com/livepeer/livepeer-wowza/issues/25)

### 0.0.2 (2019-08-07)

### 0.0.1 (2019-07-25)
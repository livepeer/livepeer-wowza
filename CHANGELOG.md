# Changelog

All notable changes to this project will be documented in this file. See [standard-version](https://github.com/conventional-changelog/standard-version) for commit guidelines.

## [0.3.0](https://github.com/livepeer/livepeer-wowza/compare/0.2.1...0.3.0) (2020-02-13)


### Features

* **wowza:** omg I'm downloading multipart data! ([8a28017](https://github.com/livepeer/livepeer-wowza/commit/8a2801768111d9b515a5e1d6a0167f9e90bac1e4))
* **wowza:** successfully parsing multipart return into byte arrays ([8e26823](https://github.com/livepeer/livepeer-wowza/commit/8e26823637e1ad46889516a1fbc502c9a277e0d2))


### Bug Fixes

* **ci:** add missing ffmpeg4 ppa ([6e1fbe8](https://github.com/livepeer/livepeer-wowza/commit/6e1fbe8f552e837a04dbf8d34928929b82e03e75))

### [0.2.1](https://github.com/livepeer/livepeer-wowza/compare/0.2.0...0.2.1) (2019-12-02)


### Features

* **wowza:** add livepeer.org/broadcaster-url ([f334fa2](https://github.com/livepeer/livepeer-wowza/commit/f334fa2))
* **wowza:** use livepeer.org/broadcaster-url for pulling back streams too ([c03dd1e](https://github.com/livepeer/livepeer-wowza/commit/c03dd1e))

## [0.2.0](https://github.com/livepeer/livepeer-wowza/compare/0.1.7...0.2.0) (2019-11-08)


### Bug Fixes

* **wowza:** more logging on sendMediaSegment ([5edc5e6](https://github.com/livepeer/livepeer-wowza/commit/5edc5e6))
* **wowza:** POST more spec-compliant streams, handle errors ([e2438f4](https://github.com/livepeer/livepeer-wowza/commit/e2438f4))
* **wowza:** use httpOriginMode on the test server ([4ed7858](https://github.com/livepeer/livepeer-wowza/commit/4ed7858))


### Features

* **wowza:** add ability to match source fps ([b11ab3d](https://github.com/livepeer/livepeer-wowza/commit/b11ab3d))
* **wowza:** disable duplicate streams by default, introduce setting ([68927b4](https://github.com/livepeer/livepeer-wowza/commit/68927b4))

### [0.1.7](https://github.com/livepeer/livepeer-wowza/compare/0.1.6...0.1.7) (2019-10-30)


### Bug Fixes

* **wowza:** stream groups recover from stream reconnect ([aab863d](https://github.com/livepeer/livepeer-wowza/commit/aab863d))
* **wowza:** tweak maximum HLS playlist sizes ([786e2e4](https://github.com/livepeer/livepeer-wowza/commit/786e2e4))

### [0.1.6](https://github.com/livepeer/livepeer-wowza/compare/0.1.5...0.1.6) (2019-10-23)


### Bug Fixes

* **wowza:** add content-resolution http header ([20d6f90](https://github.com/livepeer/livepeer-wowza/commit/20d6f90))
* **wowza:** use api-generated stream urls, reduces stuttering ([0e38564](https://github.com/livepeer/livepeer-wowza/commit/0e38564))

### [0.1.5](https://github.com/livepeer/livepeer-wowza/compare/0.1.4...0.1.5) (2019-10-21)


### Bug Fixes

* **wowza:** don't stop the transcode stream on the "stop" action ([2dcff21](https://github.com/livepeer/livepeer-wowza/commit/2dcff21)), closes [#39](https://github.com/livepeer/livepeer-wowza/issues/39)

### [0.1.4](https://github.com/livepeer/livepeer-wowza/compare/0.1.3...0.1.4) (2019-09-05)


### Bug Fixes

* **wowza:** handle more creative transcode rendition names ([96c92cb](https://github.com/livepeer/livepeer-wowza/commit/96c92cb))


### Features

* **wowza:** implement stream duplication for stream targets ([9b7100c](https://github.com/livepeer/livepeer-wowza/commit/9b7100c))

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

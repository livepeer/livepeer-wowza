FROM wowzamedia/wowza-streaming-engine-linux@sha256:904d95965cfdbec477a81374fcd22dfc48db1972e690dacb80d2114a8d597f95 as builder
RUN apt-get update && apt-get install -y openjdk-8-jdk-headless curl
# Ubuntu ships Ant with a Java 11 dependency, so we grab it manually.
WORKDIR /ant
ENV PATH $PATH:/ant/apache-ant-1.10.6/bin
RUN curl -o ant.tar.gz https://apache.org/dist/ant/binaries/apache-ant-1.10.6-bin.tar.gz && tar xzf ant.tar.gz
WORKDIR /build/wowza
RUN mkdir /build/bin
ADD build.xml build.xml
ENV PATH $PATH:/usr/local/WowzaStreamingEngine/jre/bin
ADD build-trust-store.sh build-trust-store.sh
RUN ./build-trust-store.sh /usr/local/WowzaStreamingEngine/jre
RUN cp /usr/local/WowzaStreamingEngine/jre/jre/lib/security/cacerts ./livepeer_cacerts
ARG version
ENV VERSION=$version
ADD src src
RUN sed -i "s/UNKNOWN_VERSION/${VERSION}/g" src/org/livepeer/LivepeerWowza/LivepeerVersion.java
RUN mkdir bin && ant -lib /usr/local/WowzaStreamingEngine/lib -Dwowza.lib.dir=/usr/local/WowzaStreamingEngine/lib

FROM debian:sid as installer
RUN apt-get update && apt-get install -y golang build-essential libxml2-dev libonig-dev liblzma-dev zlib1g-dev libgmp-dev libicu-dev
WORKDIR /go/src/github.com/livepeer/livepeer-wowza
ADD installation_script.go installation_script.go
RUN apt-get install -y git
ENV GOPATH /go
RUN go get .
# -static -lm -lz -ldl -licuuc -licudata
RUN go build -tags netgo -o install_livepeer_wowza -ldflags '-extldflags "-static -lz -licuuc -llzma  -licudata -lm -ldl -lstdc++ -lxml2"' installation_script.go
RUN tar czvf install_livepeer_wowza.linux.tar.gz install_livepeer_wowza
RUN apt-get install -y gcc-multilib gcc-mingw-w64-x86-64
RUN apt-get install -y win-iconv-mingw-w64-dev
RUN apt-get install -y curl
RUN curl http://download.icu-project.org/files/icu4c/57.1/icu4c-57_1-src.tgz | tar xfz - \
  && mkdir icu/build \
  && cd icu/build \
  && ../source/configure \
  && make -j`nproc` \
  && cd ../..
RUN apt-get install -y g++-mingw-w64-x86-64
RUN mkdir -p icu/build-cross \
  && cd icu/build-cross \
  && CPPFLAGS="-I/usr/x86_64-w64-mingw32/include" \
  LDFLAGS="-L/usr/x86_64-w64-mingw32/lib" \
  CC=x86_64-w64-mingw32-gcc CXX=x86_64-w64-mingw32-g++ \
  PKG_CONFIG_LIBDIR=/usr/x86_64-w64-mingw32/lib/pkgconfig \
  ../source/runConfigureICU MinGW \
  --host=x86_64-w64-mingw32 \
  --prefix=/usr/x86_64-w64-mingw32 \
  --enable-static --disable-shared --disable-strict \
  --with-cross-build=`pwd`/../build \
  && make \
  && make install \
  && cd ../..
RUN apt-get install -y autoconf libtool automake
RUN git clone -b v2.9.4 --depth=1 https://github.com/GNOME/libxml2.git
RUN cd libxml2 && CPPFLAGS="-I/usr/x86_64-w64-mingw32/include" \
  LDFLAGS="-L/usr/x86_64-w64-mingw32/lib" \
  CC=x86_64-w64-mingw32-gcc CXX=x86_64-w64-mingw32-g++ \
  PKG_CONFIG_LIBDIR=/usr/x86_64-w64-mingw32/lib/pkgconfig \
  ./autogen.sh \
  --host=x86_64-w64-mingw32 \
  --prefix=/usr/x86_64-w64-mingw32 \
  --enable-static --disable-shared --disable-strict
RUN cd libxml2 && make && make install
RUN GOOS=windows GOARCH=amd64 \
  CGO_ENABLED=1 CXX=x86_64-w64-mingw32-g++ CC=x86_64-w64-mingw32-gcc \
  go build -tags netgo -o install_livepeer_wowza.exe -ldflags '-extldflags "-static -lz -licuuc -llzma -licudata -lm -ldl -lstdc++ -liconv -lxml2 -Wl,-Bdynamic"' installation_script.go

# Just a test to make sure the thing runs
FROM wowzamedia/wowza-streaming-engine-linux@sha256:904d95965cfdbec477a81374fcd22dfc48db1972e690dacb80d2114a8d597f95 as install-test
RUN apt-get update && apt-get install -y ca-certificates
COPY --from=installer /go/src/github.com/livepeer/livepeer-wowza/install_livepeer_wowza /install_livepeer_wowza
RUN /install_livepeer_wowza -apikey abc123

FROM wowzamedia/wowza-streaming-engine-linux@sha256:904d95965cfdbec477a81374fcd22dfc48db1972e690dacb80d2114a8d597f95
ADD etc/WowzaStreamingEngine.conf /etc/supervisor/conf.d/WowzaStreamingEngine.conf
ADD etc/WowzaStreamingEngineManager.conf /etc/supervisor/conf.d/WowzaStreamingEngineManager.conf
ADD etc/Server.xml /usr/local/WowzaStreamingEngine/conf/Server.xml
ADD etc/Application.xml /usr/local/WowzaStreamingEngine/conf/live/Application.xml

COPY --from=installer /go/src/github.com/livepeer/livepeer-wowza/install_livepeer_wowza.linux.tar.gz /usr/local/install_livepeer_wowza.linux.tar.gz
COPY --from=builder /usr/local/WowzaStreamingEngine/lib/LivepeerWowza.jar /usr/local/WowzaStreamingEngine/lib/LivepeerWowza.jar
RUN chown wowza:wowza /usr/local/WowzaStreamingEngine/lib/LivepeerWowza.jar && chmod 775 /usr/local/WowzaStreamingEngine/lib/LivepeerWowza.jar

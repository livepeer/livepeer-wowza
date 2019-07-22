FROM wowzamedia/wowza-streaming-engine-linux@sha256:904d95965cfdbec477a81374fcd22dfc48db1972e690dacb80d2114a8d597f95 as builder
RUN apt-get update && apt-get install -y openjdk-8-jdk-headless curl
# Ubuntu ships Ant with a Java 11 dependency, so we grab it manually.
WORKDIR /ant
ENV PATH $PATH:/ant/apache-ant-1.10.6/bin
RUN curl -o ant.tar.gz https://apache.org/dist/ant/binaries/apache-ant-1.10.6-bin.tar.gz && tar xzvf ant.tar.gz
WORKDIR /build/wowza
RUN mkdir /build/bin
ADD build.xml build.xml
ADD cert cert
ENV PATH $PATH:/usr/local/WowzaStreamingEngine/jre/bin
ADD build-trust-store.sh build-trust-store.sh
RUN ./build-trust-store.sh /usr/local/WowzaStreamingEngine/jre
RUN cp /usr/local/WowzaStreamingEngine/jre/jre/lib/security/cacerts ./livepeer_cacerts
ADD src src
RUN mkdir bin && ant -lib /usr/local/WowzaStreamingEngine/lib -Dwowza.lib.dir=/usr/local/WowzaStreamingEngine/lib

FROM wowzamedia/wowza-streaming-engine-linux@sha256:904d95965cfdbec477a81374fcd22dfc48db1972e690dacb80d2114a8d597f95

ADD etc/WowzaStreamingEngine.conf /etc/supervisor/conf.d/WowzaStreamingEngine.conf
ADD etc/WowzaStreamingEngineManager.conf /etc/supervisor/conf.d/WowzaStreamingEngineManager.conf
ADD etc/Server.xml /usr/local/WowzaStreamingEngine/conf/Server.xml
ADD etc/Application.xml /usr/local/WowzaStreamingEngine/conf/live/Application.xml

COPY --from=builder /usr/local/WowzaStreamingEngine/lib/LivepeerWowza.jar /usr/local/WowzaStreamingEngine/lib/LivepeerWowza.jar
RUN chown wowza:wowza /usr/local/WowzaStreamingEngine/lib/LivepeerWowza.jar && chmod 775 /usr/local/WowzaStreamingEngine/lib/LivepeerWowza.jar

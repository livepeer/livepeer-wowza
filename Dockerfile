FROM wowzamedia/wowza-streaming-engine-linux@sha256:904d95965cfdbec477a81374fcd22dfc48db1972e690dacb80d2114a8d597f95

ADD etc/WowzaStreamingEngine.conf /etc/supervisor/conf.d/WowzaStreamingEngine.conf
ADD etc/WowzaStreamingEngineManager.conf /etc/supervisor/conf.d/WowzaStreamingEngineManager.conf
ADD etc/Server.xml /usr/local/WowzaStreamingEngine/conf/Server.xml
ADD etc/Application.xml /usr/local/WowzaStreamingEngine/conf/live/Application.xml

ADD LivepeerWowza.jar /usr/local/WowzaStreamingEngine/lib/LivepeerWowza.jar

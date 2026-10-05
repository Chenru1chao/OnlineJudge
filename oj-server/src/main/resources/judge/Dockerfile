FROM ubuntu:24.04
RUN sed -i "s|http://archive.ubuntu.com/ubuntu/|http://mirrors.tuna.tsinghua.edu.cn/ubuntu/|g; s|http://security.ubuntu.com/ubuntu/|http://mirrors.tuna.tsinghua.edu.cn/ubuntu/|g" /etc/apt/sources.list.d/ubuntu.sources \
 && apt-get update \
 && apt-get install -y openjdk-17-jdk-headless time coreutils \
 && rm -rf /var/lib/apt/lists/*
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH=$JAVA_HOME/bin:$PATH
RUN useradd -u 2000 -M -s /usr/sbin/nologin isolated
FROM nginx
RUN apt-get update
RUN apt-get install -y iproute
COPY default.conf /etc/nginx/conf.d/default.conf
COPY static-html-directory /usr/share/nginx/html
COPY ssl.crt /etc/ssl/certs/myssl.crt
COPY ssl.key /etc/ssl/private/myssl.key

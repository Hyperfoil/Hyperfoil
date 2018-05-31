FROM nginx
RUN apt-get update
RUN apt-get install -y iproute
COPY default.conf /etc/nginx/conf.d/default.conf
COPY static-html-directory /usr/share/nginx/html

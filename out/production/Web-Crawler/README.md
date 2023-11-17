Project 5 (Web Crawler): Joseph Kwilman + Andrew Panzone

Overview:
The Crawler class is responsible for logging in and crawling a web server.
It takes in a server, port, username, and password. It is compatible with CSRF token authentication for
the login process, then saves a cookie of the session id to stay logged in with its communications.
NetUtil is a class that handles communication over TLS. The method communicate opens a new TLS socket
with the specified server and port. Then, it sends the request message and reads in the response.
The class HTTPResponse represents an HTTP response. It parses the input into a status code, a header,
cookies, and a body.
The Crawler works by adding new, viable fakebook links to a queue. It polls from the queue and searches 
the associated html webpage for the flags and other viable links. It ends when there are no more pages to find
or 5 flags have been found.


Challenges:
The POST operation was tough to get right. It took us a long time to figure out how to structure the header
and content to successfully implement the csrf tokens. Our initial program had an extremely long run time(30-40 minutes),
but threading sped up the program (8-10 minutes).

Testing: test the code by running it against the default server "www.3700.network".
The code logs in the with username and password passed through the command line.
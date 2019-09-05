// This is a little node.js server that's useful for testing Stream Targets such
// as akamai. Just run it, set your IP as the destination host, and you're good
// to go!
const http = require("http");
const port = 80;

const requestHandler = (req, res) => {
  let size = 0;
  console.log(`${req.method} ${req.url} start`);
  req.on("data", chunk => {
    size += chunk.length;
  });
  req.on("end", () => {
    console.log(`${req.method} ${req.url} end ${size}`);
    res.end("ok");
  });
};

const server = http.createServer(requestHandler);

server.listen(port, err => {
  if (err) {
    return console.log("something bad happened", err);
  }

  console.log(`server is listening on ${port}`);
});

const TARGET = process.env.npm_lifecycle_event;

module.exports = Object.freeze({
  LOCATION: TARGET == 'start' || TARGET == null ?
  'http://localhost:8080/' : document.location.toString(),
});

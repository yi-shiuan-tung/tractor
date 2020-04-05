const path = require('path');

module.exports = [{
  entry: './js/index.js',
  mode: 'development',
  output: {
    filename: 'bundle.js',
    path: path.resolve(__dirname, 'src/main/resources/assets'),
  },
}];

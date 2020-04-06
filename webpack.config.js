const path = require('path');

module.exports = [{
  entry: ['./js/index.js', './js/lobby.js', './js/game.js'],
  mode: 'development',
  output: {
    filename: 'bundle.js',
    path: path.resolve(__dirname, 'src/main/resources/assets'),
  },
  module: {
    rules: [
      {
        test: /.js$/,
        include: [path.resolve(__dirname, 'js')],
        loader: 'babel-loader',
        options: {
          plugins: ['syntax-dynamic-import', '@babel/plugin-proposal-class-properties'],
          presets: ['@babel/preset-env', '@babel/preset-react'],
        }
      }
    ]
  },
}];

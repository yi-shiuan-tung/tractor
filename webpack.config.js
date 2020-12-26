const path = require('path');

const TARGET = process.env.npm_lifecycle_event;

const common = {
  entry: './client/index.js',
  mode: 'production',
  output: {
    filename: 'bundle.js',
    path: path.resolve(__dirname, 'src/main/resources/assets'),
  },
  module: {
    rules: [
      {
        test: /.js$/,
        include: [path.resolve(__dirname, 'client')],
        loader: 'babel-loader',
        options: {
          plugins: ['syntax-dynamic-import', '@babel/plugin-proposal-class-properties'],
          presets: ['@babel/preset-env', '@babel/preset-react'],
        }
      },
      {
        test: /\.css$/,
          use: [
            'style-loader',
            'css-loader'
          ],
      },
      {
        test: /\.(mp3|png|ttf)$/,
        use: [
          'file-loader',
        ],
      },
    ]
  },
};

if (TARGET === 'start' || !TARGET) {
  module.exports = Object.assign({}, common, {
    devServer: {
      contentBase: path.join(__dirname, 'client'),
      port: 3000,
      proxy: {
        '/': {
          target: 'http://localhost:8080',
          secure: false,
          prependPath: false
        },
        '/tractor': {
          target: 'ws://localhost:8080',
          ws: true,
          secure: false,
          prependPath: false
        }
      },
      publicPath: 'http://localhost:3000/',
      historyApiFallback: true,
    },
    devtool: 'source-map',
    mode: 'development',
  });
}

if (TARGET === 'build') {
  module.exports = common;
}


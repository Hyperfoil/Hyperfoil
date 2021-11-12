package io.hyperfoil.clustering.webcli;

class MissingFileException extends RuntimeException {
   final String file;

   MissingFileException(String file) {
      this.file = file;
   }
}

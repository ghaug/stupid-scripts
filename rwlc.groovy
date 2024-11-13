#!/usr/bin/env groovy


/**
 * @author gunter
 *
 */
class RandomWireLengthCalculator {
  void giveHelp() {
    println 'rwlc [options] frequencies'
    println '    options: '
    println '      -m n       maximum wire length in meters (default 25, min 5)'
    println '      -x n       multiples of lambda*.5 to consider (default 4, min 2)'
    println '    frequencies: frequency1 frequency2 [frequency3...]:'
    println '                 frequencies of interest in kHz'
  }

  // here we go: run
  def run(args) {
    try {
      // first of all parse command line arguments
      def multiples = 4
      def max = 25
      def ignore = 0
      def exit = false
      def frequencies = []
      def numFrequencies = 0

      args.eachWithIndex { arg, i ->
        if (ignore == 0) {
          switch (arg) {
            case '-m' :
              if (args.length > i + 1) {
                max = args[i + 1] as int
              } else {
                throw new WrongArgException('Missing value, try -h')
              }
              if (max < 5) throw new WrongArgException('Min wire length is 5m, try -h')
              ignore = 1
              break
            case '-x' :
              if (args.length > i + 1) {
                multiples = args[i + 1] as int
              } else {
                throw new WrongArgException('Missing value, try -h')
              }
              if (multiples < 2) throw new WrongArgException('Need at least 2 multiples, try -h')
              ignore = 1
              break
            case '-h' :
              giveHelp()
              exit = true
              return
            case { arg.substring(0, 1) != '-' } :
              frequencies[numFrequencies++] = arg as int
              break
            default:
              throw new WrongArgException("Don't understand " + arg + ', try -h')
          }
        } else {
          ignore--
        }
      }
      if (exit) return
      if (numFrequencies < 2) throw new WrongArgException("At least two frequencies are required")

      def lambda2mult = []
      frequencies.sort()
      println "RANDOM WIRE LENGTH CALCULATOR"
      println "Considering the following frequencies up to ${multiples} * lambda / 2:"
      frequencies.eachWithIndex{ freq, i ->
        println "${i}: ${freq}kHz -> lambda: ${kHz2meters(freq)} lambda/2: ${kHz2meters(freq) * 0.5}"
        for (def n = 1; n <= multiples; ++n) lambda2mult << kHz2meters(freq) * 0.5 * n
      }
      lambda2mult.sort();
      def diff = 0.0
      def diff2
      def index = 0
      def index2
      for (def i = 1; i < lambda2mult.size(); ++i) {
        if ((lambda2mult[i - 1] - lambda2mult[i]).abs() > diff && lambda2mult[i] <= max) {
          diff2 = diff
          index2 = index
          diff = (lambda2mult[i - 1] - lambda2mult[i]).abs()
          index = i
        }
      }
      def dist = diff * 0.5
      def dist2 = diff2 * 0.5
      println "Results:"
      println "Best length: ${lambda2mult[index - 1] + dist}m (${dist}m from ${lambda2mult[index - 1]}m and ${lambda2mult[index]}m)"
      println "Second best length: ${lambda2mult[index2 - 1] + dist2}m (${dist2}m from ${lambda2mult[index2 - 1]}m and ${lambda2mult[index2]}m)"
    }
    catch (WrongArgException e) {
      println 'Wrong command line argument! ' + e.text()
    }
    catch (Throwable t) {
      throw t
    }
  }
  // main, not much to do here
  static main(args) {
    def rwlc = new RandomWireLengthCalculator()
    rwlc.run(args)
  }

  double kHz2meters(f) {
    return 299792458.0 / (f * 1000)
  }
}

class WrongArgException
extends Exception {
  String txt
  WrongArgException(String s) { super(s); txt = s }
  String text() { return txt }
}

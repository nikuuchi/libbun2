#!python
#coding:utf-8
import sys, os, json, re, fnmatch, subprocess
from os.path import isfile, join

def log(level, message):
    print "[" + level + "] " + message

def collect_files(path, pattern):
    files = [join(path, f) for f in os.listdir(path) if isfile(join(path, f)) ]
    files = fnmatch.filter(files, "*." + pattern)
    return map(lambda x : os.path.splitext(x)[0], files)

class TestRunner:
    def __init__(self, verbose):
       self.testcases = None
       self.conf      = None
       self.verbose   = verbose

    def load_config(self, root, json_path):
        if os.path.exists(json_path) == False:
            log("warning", json_path + " is not exists")
            return False
        f = open(json_path, "r")
        conf = json.load(f)
        ipattern = conf["input.ext"]
        opattern = conf["output.ext"]
        if conf["peg"] == None:
            conf["peg"] = ""
        if ipattern == None:
            log("warning", "input.ext is not defined")
            return False
        if opattern == None:
            log("info", "output.ext is not defined. use " + ipattern + ".out")
            conf["output.ext"] = ipattern + ".out"

        conf["peg"] = conf["peg"].replace("#{SOURCE_ROOT}", root)
        self.conf = conf
        return True

    def prepare(self, root, path):
        json_path = path + "/run.json"
        if self.load_config(root, json_path) == False:
            return False

        inputs  = collect_files(path, self.conf["input.ext"])
        inputs.sort();
        for f in inputs:
            result = f + "." + self.conf["output.ext"]
            if not(os.path.exists(result)):
                log("warning", result + " is not found")
                return False

        self.testcases = inputs
        return True

    def run_once(self, root, peg, input_file):
        cmd = ['java', '-ea', '-jar', root + '/libbun2.jar',
                '--verbose:ast', '--parse-only',
                '--peg', peg, input_file]
        print "# " + " ".join(cmd)
        p = subprocess.Popen(cmd,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                shell=False)
        exit_status = p.wait()
        output = "".join(p.stdout.readlines())
        errors = "".join(p.stderr.readlines())
        return (exit_status, output, errors)

    def run(self):
        executed = 0
        passed   = 0
        failed_file_names = []
        if len(self.testcases) == 0:
            return

        for f in self.testcases:
            input_file  = f + "." + self.conf["input.ext"]
            output_file = f + "." + self.conf["output.ext"]
            syntax_file = self.conf["peg"]
            (status, output, erros) = self.run_once(root, syntax_file, input_file)
            #if self.verbose:
            #    print 'return: %d' % (status)
            #    print 'stdout: %s' % (output, )
            #    print 'stderr: %s' % (errors, )
            executed += 1
            expected = open(output_file, "r").read()
            if status == 0 and expected == output:
                passed += 1
            else:
                failed_file_names.append(input_file)
            if self.verbose:
                print '-----------------------'
                print 'result:\n%s' % (output)
                print '-----------------------'
                print 'expected\n%s' % (expected)
                print '-----------------------'
        return executed, passed, failed_file_names

def printTestResult(executed, passed, failed_file_names):
    print "# of testcases: %d, # of OK: %d, # of FAILED: %d" % (
            executed, passed, executed - passed)
    if executed - passed > 0:
        print '\nFAILED File:'
        for name in failed_file_names:
            print "    %s" % (name)

if __name__ == '__main__':
    root = os.path.abspath(os.path.dirname(__file__) + "/../../")
    dirs = filter(lambda x : x != "tool", os.listdir(root + "/test-peg/"))
    target = ""
    if len(sys.argv) > 1:
        target = sys.argv[1]
        dirs = filter(lambda x : x == target, dirs)

    executed = 0
    passed   = 0
    failed_file_names = []
    for path in dirs:
        runner = TestRunner(True)
        if runner.prepare(root, root + "/test-peg/" + path):
            each_executed, each_passed, each_failed_file_names = runner.run()
            executed += each_executed
            passed   += each_passed
            failed_file_names += each_failed_file_names
    printTestResult(executed, passed, failed_file_names)

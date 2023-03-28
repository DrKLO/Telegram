import sys
from zipfile import ZipFile

def compareFiles(first, second):
	while True:
		firstBytes = first.read(4096)
		secondBytes = second.read(4096)
		if firstBytes != secondBytes:
			return False

		if firstBytes == b"" and secondBytes == b"":
			break

	return True

def compare(first, second):
	FILES_TO_IGNORE = ["META-INF/MANIFEST.MF", "META-INF/CERT.RSA", "META-INF/CERT.SF"]

	firstZip = ZipFile(first, 'r')
	secondZip = ZipFile(second, 'r')

	firstList = list(filter(lambda firstInfo: firstInfo.filename not in FILES_TO_IGNORE, firstZip.infolist()))
	secondList = list(filter(lambda secondInfo: secondInfo.filename not in FILES_TO_IGNORE, secondZip.infolist()))

	if len(firstList) != len(secondList):
	    print("APKs has different amount of files (%d != %d)" % (len(firstList), len(secondList)))
	    return False

	for firstInfo in firstList:
		found = False
		for secondInfo in secondList:
			if firstInfo.filename == secondInfo.filename:
				found = True
				firstFile = firstZip.open(firstInfo, 'r')
				secondFile = secondZip.open(secondInfo, 'r')

				if compareFiles(firstFile, secondFile) != True:
					print("APK file %s does not match" % firstInfo.filename)
					return False

				secondList.remove(secondInfo)
				break

		if found == False:
			print("file %s not found in second APK" % firstInfo.filename)
			return False

	if len(secondList) != 0:
		for secondInfo in secondList:
			print("file %s not found in first APK" % secondInfo.filename)
		return False

	return True

if __name__ == '__main__':
	if len(sys.argv) != 3:
		print("Usage: apkdiff <pathToFirstApk> <pathToSecondApk>")
		sys.exit(1)

	if sys.argv[1] == sys.argv[2] or compare(sys.argv[1], sys.argv[2]) == True:
		print("APKs are the same!")
	else:
		print("APKs are different!")

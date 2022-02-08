package org.telegram.messenger;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.Base64;
import android.util.SparseArray;

import java.util.Calendar;
import java.util.HashMap;

public class MrzRecognizer {

	public static Result recognize(Bitmap bitmap, boolean tryDriverLicenseFirst) {
		Result res;
		if (tryDriverLicenseFirst) {
			res = recognizeBarcode(bitmap);
			if (res != null)
				return res;
		}
		try {
			res = recognizeMRZ(bitmap);
			if (res != null)
				return res;
		} catch (Exception ignore) {
		}
		if (!tryDriverLicenseFirst) {
			res = recognizeBarcode(bitmap);
			if (res != null)
				return res;
		}
		return null;
	}

	private static Result recognizeBarcode(Bitmap bitmap) {
		/*
		BarcodeDetector detector = new BarcodeDetector.Builder(ApplicationLoader.applicationContext)/*.setBarcodeFormats(Barcode.PDF417).build();
		if (bitmap.getWidth() > 1500 || bitmap.getHeight() > 1500) {
			float scale = 1500f / Math.max(bitmap.getWidth(), bitmap.getHeight());
			bitmap = Bitmap.createScaledBitmap(bitmap, Math.round(bitmap.getWidth() * scale), Math.round(bitmap.getHeight() * scale), true);
		}
		SparseArray<Barcode> barcodes = detector.detect(new Frame.Builder().setBitmap(bitmap).build());
		for (int i = 0; i < barcodes.size(); i++) {
			Barcode code = barcodes.valueAt(i);
			if (code.valueFormat == Barcode.DRIVER_LICENSE && code.driverLicense != null) { // BarcodeDetector has built-in support for North American driver licenses/IDs
				Result res = new Result();
				res.type = "ID".equals(code.driverLicense.documentType) ? Result.TYPE_ID : Result.TYPE_DRIVER_LICENSE;
				switch (code.driverLicense.issuingCountry) {
					case "USA":
						res.nationality = res.issuingCountry = "US";
						break;
					case "CAN":
						res.nationality = res.issuingCountry = "CA";
						break;
				}
				res.firstName = capitalize(code.driverLicense.firstName);
				res.lastName = capitalize(code.driverLicense.lastName);
				res.middleName = capitalize(code.driverLicense.middleName);
				res.number = code.driverLicense.licenseNumber;
				if (code.driverLicense.gender != null) {
					switch (code.driverLicense.gender) {
						case "1":
							res.gender = Result.GENDER_MALE;
							break;
						case "2":
							res.gender = Result.GENDER_FEMALE;
							break;
					}
				}
				int yearOffset, monthOffset, dayOffset;
				if ("USA".equals(res.issuingCountry)) {
					yearOffset = 4;
					dayOffset = 2;
					monthOffset = 0;
				} else {
					yearOffset = 0;
					monthOffset = 4;
					dayOffset = 6;
				}
				try {
					if (code.driverLicense.birthDate != null && code.driverLicense.birthDate.length() == 8) {
						res.birthYear = Integer.parseInt(code.driverLicense.birthDate.substring(yearOffset, yearOffset + 4));
						res.birthMonth = Integer.parseInt(code.driverLicense.birthDate.substring(monthOffset, monthOffset + 2));
						res.birthDay = Integer.parseInt(code.driverLicense.birthDate.substring(dayOffset, dayOffset + 2));
					}
					if (code.driverLicense.expiryDate != null && code.driverLicense.expiryDate.length() == 8) {
						res.expiryYear = Integer.parseInt(code.driverLicense.expiryDate.substring(yearOffset, yearOffset + 4));
						res.expiryMonth = Integer.parseInt(code.driverLicense.expiryDate.substring(monthOffset, monthOffset + 2));
						res.expiryDay = Integer.parseInt(code.driverLicense.expiryDate.substring(dayOffset, dayOffset + 2));
					}
				} catch (NumberFormatException ignore) {
				}

				return res;
			} else if (code.valueFormat == Barcode.TEXT && code.format == Barcode.PDF417) { // Russian driver licenses (new-ish ones) use a non-very-much-documented format
				// base64(number|issue date|expiry date|last name|first name|middle/father name|birth date|categories|???|???)
				// all dates are YYYYMMDD, names are capital cyrillic letters in Windows-1251, categories are comma separated
				if (code.rawValue.matches("^[A-Za-z0-9=]+$")) {
					try {
						byte[] _data = Base64.decode(code.rawValue, 0);
						String[] data = new String(_data, "windows-1251").split("\\|");
						if (data.length >= 10) {
							Result res = new Result();
							res.type = Result.TYPE_DRIVER_LICENSE;
							res.nationality = res.issuingCountry = "RU";
							res.number = data[0];
							res.expiryYear = Integer.parseInt(data[2].substring(0, 4));
							res.expiryMonth = Integer.parseInt(data[2].substring(4, 6));
							res.expiryDay = Integer.parseInt(data[2].substring(6));
							res.lastName = capitalize(cyrillicToLatin(data[3]));
							res.firstName = capitalize(cyrillicToLatin(data[4]));
							res.middleName = capitalize(cyrillicToLatin(data[5]));
							res.birthYear = Integer.parseInt(data[6].substring(0, 4));
							res.birthMonth = Integer.parseInt(data[6].substring(4, 6));
							res.birthDay = Integer.parseInt(data[6].substring(6));
							return res;
						}
					} catch (Exception ignore) {
					}
				}
			}
		}*/
		return null;
	}

	private static Result recognizeMRZ(Bitmap bitmap) {
		Bitmap smallBitmap;
		float scale = 1;
		if (bitmap.getWidth() > 512 || bitmap.getHeight() > 512) {
			scale = 512f / Math.max(bitmap.getWidth(), bitmap.getHeight());
			smallBitmap = Bitmap.createScaledBitmap(bitmap, Math.round(bitmap.getWidth() * scale), Math.round(bitmap.getHeight() * scale), true);
		} else {
			smallBitmap = bitmap;
		}

		int[] points = findCornerPoints(smallBitmap);
		float pointsScale = 1f / scale;
		if (points != null) {
			Point topLeft = new Point(points[0], points[1]), topRight = new Point(points[2], points[3]),
					bottomLeft = new Point(points[4], points[5]), bottomRight = new Point(points[6], points[7]);
			if (topRight.x < topLeft.x) {
				Point tmp = topRight;
				topRight = topLeft;
				topLeft = tmp;
				tmp = bottomRight;
				bottomRight = bottomLeft;
				bottomLeft = tmp;
			}
			double topLength = Math.hypot(topRight.x - topLeft.x, topRight.y - topLeft.y);
			double bottomLength = Math.hypot(bottomRight.x - bottomLeft.x, bottomRight.y - bottomLeft.y);
			double leftLength = Math.hypot(bottomLeft.x - topLeft.x, bottomLeft.y - topLeft.y);
			double rightLength = Math.hypot(bottomRight.x - topRight.x, bottomRight.y - topRight.y);
			double tlRatio = topLength / leftLength;
			double trRatio = topLength / rightLength;
			double blRatio = bottomLength / leftLength;
			double brRatio = bottomLength / rightLength;
			if ((tlRatio >= 1.35 && tlRatio <= 1.75) && (blRatio >= 1.35 && blRatio <= 1.75) && (trRatio >= 1.35 && trRatio <= 1.75) && (brRatio >= 1.35 && brRatio <= 1.75)) {
				double avgRatio = (tlRatio + trRatio + blRatio + brRatio) / 4.0;
				Bitmap tmp = Bitmap.createBitmap(1024, (int) Math.round(1024 / avgRatio), Bitmap.Config.ARGB_8888);
				Canvas c = new Canvas(tmp);
				float[] dst = new float[]{
						0,
						0,
						tmp.getWidth(),
						0,
						tmp.getWidth(),
						tmp.getHeight(),
						0,
						tmp.getHeight()
				};
				float[] src = new float[]{
						topLeft.x * pointsScale,
						topLeft.y * pointsScale,
						topRight.x * pointsScale,
						topRight.y * pointsScale,
						bottomRight.x * pointsScale,
						bottomRight.y * pointsScale,
						bottomLeft.x * pointsScale,
						bottomLeft.y * pointsScale
				};
				Matrix perspMatrix = new Matrix();
				perspMatrix.setPolyToPoly(src, 0, dst, 0, src.length >> 1);
				c.drawBitmap(bitmap, perspMatrix, new Paint(Paint.FILTER_BITMAP_FLAG));
				bitmap = tmp;
			}
		} else if (bitmap.getWidth() > 1500 || bitmap.getHeight() > 1500) {
			scale = 1500f / Math.max(bitmap.getWidth(), bitmap.getHeight());
			bitmap = Bitmap.createScaledBitmap(bitmap, Math.round(bitmap.getWidth() * scale), Math.round(bitmap.getHeight() * scale), true);
		}

		Bitmap binaryBitmap = null;
		Rect[][] charRects = null;
		int maxLength = 0;
		int lineCount = 0;
		for (int i = 0; i < 3; i++) {
			Matrix m = null;
			Bitmap toProcess = bitmap;
			switch (i) {
				case 1:
					m = new Matrix();
					m.setRotate(1f, bitmap.getWidth() / 2, bitmap.getHeight() / 2);
					break;
				case 2:
					m = new Matrix();
					m.setRotate(-1f, bitmap.getWidth() / 2, bitmap.getHeight() / 2);
					break;
			}
			if (m != null) {
				toProcess = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
			}
			binaryBitmap = Bitmap.createBitmap(toProcess.getWidth(), toProcess.getHeight(), Bitmap.Config.ALPHA_8);
			charRects = binarizeAndFindCharacters(toProcess, binaryBitmap);
			if (charRects == null)
				return null;
			for (Rect[] rects : charRects) {
				maxLength = Math.max(rects.length, maxLength);
				if (rects.length > 0)
					lineCount++;
			}
			if (lineCount >= 2 && maxLength >= 30) {
				break;
			}
		}
		if (maxLength < 30 || lineCount < 2)
			return null;
		Bitmap chrBitmap = Bitmap.createBitmap(10 * charRects[0].length, 15 * charRects.length, Bitmap.Config.ALPHA_8);
		Canvas chrCanvas = new Canvas(chrBitmap);
		Paint aaPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
		Rect dst = new Rect(0, 0, 10, 15);
		int x, y = 0;
		for (Rect[] line : charRects) {
			x = 0;
			for (Rect rect : line) {
				dst.set(x * 10, y * 15, x * 10 + 10, y * 15 + 15);
				chrCanvas.drawBitmap(binaryBitmap, rect, dst, aaPaint);
				x++;
			}
			y++;
		}
		String mrz = performRecognition(chrBitmap, charRects.length, charRects[0].length, ApplicationLoader.applicationContext.getAssets());
		if (mrz == null)
			return null;

		String[] mrzLines = TextUtils.split(mrz, "\n");
		Result result = new Result();
		if (mrzLines.length >= 2 && mrzLines[0].length() >= 30 && mrzLines[1].length() == mrzLines[0].length()) {
			result.rawMRZ = TextUtils.join("\n", mrzLines);
			HashMap<String, String> countries = getCountriesMap();
			char type = mrzLines[0].charAt(0);
			if (type == 'P') { // passport
				result.type = Result.TYPE_PASSPORT;
				if (mrzLines[0].length() == 44) {
					result.issuingCountry = mrzLines[0].substring(2, 5);
					int lastNameEnd = mrzLines[0].indexOf("<<", 6);
					if (lastNameEnd != -1) {
						result.lastName = mrzLines[0].substring(5, lastNameEnd).replace('<', ' ').replace('0', 'O').trim();
						result.firstName = mrzLines[0].substring(lastNameEnd + 2).replace('<', ' ').replace('0', 'O').trim();
						if (result.firstName.contains("   ")) {
							result.firstName = result.firstName.substring(0, result.firstName.indexOf("   "));
						}
					}
					String number = mrzLines[1].substring(0, 9).replace('<', ' ').replace('O', '0').trim();
					int numberChecksum = checksum(number);
					if (numberChecksum == getNumber(mrzLines[1].charAt(9))) {
						result.number = number;
					}
					result.nationality = mrzLines[1].substring(10, 13);
					String birthDate = mrzLines[1].substring(13, 19).replace('O', '0').replace('I', '1');
					int birthDateChecksum = checksum(birthDate);
					if (birthDateChecksum == getNumber(mrzLines[1].charAt(19))) {
						parseBirthDate(birthDate, result);
					}
					result.gender = parseGender(mrzLines[1].charAt(20));
					String expiryDate = mrzLines[1].substring(21, 27).replace('O', '0').replace('I', '1');
					int expiryDateChecksum = checksum(expiryDate);
					if (expiryDateChecksum == getNumber(mrzLines[1].charAt(27)) || mrzLines[1].charAt(27) == '<') {
						parseExpiryDate(expiryDate, result);
					}

					// Russian internal passports use transliteration for the name and have a number one digit longer than fits into the standard MRZ format
					if ("RUS".equals(result.issuingCountry) && mrzLines[0].charAt(1) == 'N') {
						result.type = Result.TYPE_INTERNAL_PASSPORT;
						String[] names = result.firstName.split(" ");
						result.firstName = cyrillicToLatin(russianPassportTranslit(names[0]));
						if (names.length > 1)
							result.middleName = cyrillicToLatin(russianPassportTranslit(names[1]));
						result.lastName = cyrillicToLatin(russianPassportTranslit(result.lastName));
						if (result.number != null)
							result.number = result.number.substring(0, 3) + mrzLines[1].charAt(28) + result.number.substring(3);
					} else {
						result.firstName = result.firstName.replace('8', 'B');
						result.lastName = result.lastName.replace('8', 'B');
					}
					result.lastName = capitalize(result.lastName);
					result.firstName = capitalize(result.firstName);
					result.middleName = capitalize(result.middleName);
				}
			} else if (type == 'I' || type == 'A' || type == 'C') { // id
				result.type = Result.TYPE_ID;
				if (mrzLines.length == 3 && mrzLines[0].length() == 30 && mrzLines[2].length() == 30) {
					result.issuingCountry = mrzLines[0].substring(2, 5);
					String number = mrzLines[0].substring(5, 14).replace('<', ' ').replace('O', '0').trim();
					int numberChecksum = checksum(number);
					if (numberChecksum == mrzLines[0].charAt(14) - '0') {
						result.number = number;
					}

					String birthDate = mrzLines[1].substring(0, 6).replace('O', '0').replace('I', '1');
					int birthDateChecksum = checksum(birthDate);
					if (birthDateChecksum == getNumber(mrzLines[1].charAt(6))) {
						parseBirthDate(birthDate, result);
					}
					result.gender = parseGender(mrzLines[1].charAt(7));
					String expiryDate = mrzLines[1].substring(8, 14).replace('O', '0').replace('I', '1');
					int expiryDateChecksum = checksum(expiryDate);
					if (expiryDateChecksum == getNumber(mrzLines[1].charAt(14)) || mrzLines[1].charAt(14) == '<') {
						parseExpiryDate(expiryDate, result);
					}
					result.nationality = mrzLines[1].substring(15, 18);
					int lastNameEnd = mrzLines[2].indexOf("<<");
					if (lastNameEnd != -1) {
						result.lastName = mrzLines[2].substring(0, lastNameEnd).replace('<', ' ').trim();
						result.firstName = mrzLines[2].substring(lastNameEnd + 2).replace('<', ' ').trim();
					}
				} else if (mrzLines.length == 2 && mrzLines[0].length() == 36) {
					result.issuingCountry = mrzLines[0].substring(2, 5);
					if ("FRA".equals(result.issuingCountry) && type == 'I' && mrzLines[0].charAt(1) == 'D') { // French IDs use an entirely different format
						result.nationality = "FRA";
						result.lastName = mrzLines[0].substring(5, 30).replace('<', ' ').trim();
						result.firstName = mrzLines[1].substring(13, 27).replace("<<", ", ").replace('<', ' ').trim();
						String number = mrzLines[1].substring(0, 12).replace('O', '0');
						if (checksum(number) == getNumber(mrzLines[1].charAt(12))) {
							result.number = number;
						}
						String birthDate = mrzLines[1].substring(27, 33).replace('O', '0').replace('I', '1');
						if (checksum(birthDate) == getNumber(mrzLines[1].charAt(33))) {
							parseBirthDate(birthDate, result);
						}
						result.gender = parseGender(mrzLines[1].charAt(34));
						result.doesNotExpire = true;
					} else {
						int lastNameEnd = mrzLines[0].indexOf("<<");
						if (lastNameEnd != -1) {
							result.lastName = mrzLines[0].substring(5, lastNameEnd).replace('<', ' ').trim();
							result.firstName = mrzLines[0].substring(lastNameEnd + 2).replace('<', ' ').trim();
						}
						String number = mrzLines[1].substring(0, 9).replace('<', ' ').replace('O', '0').trim();
						int numberChecksum = checksum(number);
						if (numberChecksum == getNumber(mrzLines[1].charAt(9))) {
							result.number = number;
						}
						result.nationality = mrzLines[1].substring(10, 13);
						String birthDate = mrzLines[1].substring(13, 19).replace('O', '0').replace('I', '1');
						if (checksum(birthDate) == getNumber(mrzLines[1].charAt(19))) {
							parseBirthDate(birthDate, result);
						}
						result.gender = parseGender(mrzLines[1].charAt(20));
						String expiryDate = mrzLines[1].substring(21, 27).replace('O', '0').replace('I', '1');
						if (checksum(expiryDate) == getNumber(mrzLines[1].charAt(27)) || mrzLines[1].charAt(27) == '<') {
							parseExpiryDate(expiryDate, result);
						}
					}
				}
				result.firstName = capitalize(result.firstName.replace('0', 'O').replace('8', 'B'));
				result.lastName = capitalize(result.lastName.replace('0', 'O').replace('8', 'B'));
			} else {
				return null;
			}
			if (TextUtils.isEmpty(result.firstName) && TextUtils.isEmpty(result.lastName))
				return null;
			result.issuingCountry = countries.get(result.issuingCountry);
			result.nationality = countries.get(result.nationality);
			return result;
		}
		return null;
	}

	public static Result recognize(byte[] yuvData, int width, int height, int rotation) {
		Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		setYuvBitmapPixels(bmp, yuvData);
		Matrix m = new Matrix();
		m.setRotate(rotation);
		int minSize = Math.min(width, height);
		int dh = Math.round(minSize * 0.704f);
		boolean swap = rotation == 90 || rotation == 270;
		bmp = Bitmap.createBitmap(bmp, swap ? (width / 2 - dh / 2) : 0, swap ? 0 : (height / 2 - dh / 2), swap ? dh : minSize, swap ? minSize : dh, m, false);
		return recognize(bmp, false);
	}

	private static String capitalize(String s) {
		if (s == null)
			return null;
		char[] chars = s.toCharArray();
		boolean prevIsSpace = true;
		for (int i = 0; i < chars.length; i++) {
			if (!prevIsSpace && Character.isLetter(chars[i])) {
				chars[i] = Character.toLowerCase(chars[i]);
			} else {
				prevIsSpace = chars[i] == ' ';
			}
		}
		return new String(chars);
	}

	private static int checksum(String s) {
		int val = 0;
		char[] chars = s.toCharArray();
		final int[] weights = {7, 3, 1};
		for (int i = 0; i < chars.length; i++) {
			int charVal = 0;
			if (chars[i] >= '0' && chars[i] <= '9') {
				charVal = chars[i] - '0';
			} else if (chars[i] >= 'A' && chars[i] <= 'Z') {
				charVal = chars[i] - 'A' + 10;
			}
			val += charVal * weights[i % weights.length];
		}
		return val % 10;
	}

	private static void parseBirthDate(String birthDate, Result result) {
		try {
			result.birthYear = Integer.parseInt(birthDate.substring(0, 2));
			result.birthYear = result.birthYear < Calendar.getInstance().get(Calendar.YEAR) % 100 - 5 ? (2000 + result.birthYear) : (1900 + result.birthYear);
			result.birthMonth = Integer.parseInt(birthDate.substring(2, 4));
			result.birthDay = Integer.parseInt(birthDate.substring(4));
		} catch (NumberFormatException ignore) {
		}
	}

	private static void parseExpiryDate(String expiryDate, Result result) {
		try {
			if ("<<<<<<".equals(expiryDate)) {
				result.doesNotExpire = true;
			} else {
				result.expiryYear = 2000 + Integer.parseInt(expiryDate.substring(0, 2));
				result.expiryMonth = Integer.parseInt(expiryDate.substring(2, 4));
				result.expiryDay = Integer.parseInt(expiryDate.substring(4));
			}
		} catch (NumberFormatException ignore) {
		}
	}

	private static int parseGender(char gender) {
		switch (gender) {
			case 'M':
				return Result.GENDER_MALE;
			case 'F':
				return Result.GENDER_FEMALE;
			default:
				return Result.GENDER_UNKNOWN;
		}
	}

	private static String russianPassportTranslit(String s) {
		final String cyrillic = "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ";
		final String latin = "ABVGDE2JZIQKLMNOPRSTUFHC34WXY9678";
		char[] chars = s.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			int idx = latin.indexOf(chars[i]);
			if (idx != -1)
				chars[i] = cyrillic.charAt(idx);
		}
		return new String(chars);
	}

	private static String cyrillicToLatin(String s) {
		final String alphabet = "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ";
		final String[] replacements = {"A", "B", "V", "G", "D", "E", "E", "ZH", "Z", "I", "I", "K", "L", "M", "N", "O", "P", "R", "S", "T", "U", "F", "KH", "TS", "CH", "SH", "SHCH", "IE", "Y", "", "E", "IU", "IA"};
		for (int i = 0; i < replacements.length; i++) {
			s = s.replace(alphabet.substring(i, i + 1), replacements[i]);
		}
		return s;
	}

	private static int getNumber(char c) {
		if (c == 'O')
			return 0;
		if (c == 'I')
			return 1;
		if (c == 'B')
			return 8;
		return c - '0';
	}

	private static HashMap<String, String> getCountriesMap() {
		HashMap<String, String> countries = new HashMap<>();
		countries.put("AFG", "AF");
		countries.put("ALA", "AX");
		countries.put("ALB", "AL");
		countries.put("DZA", "DZ");
		countries.put("ASM", "AS");
		countries.put("AND", "AD");
		countries.put("AGO", "AO");
		countries.put("AIA", "AI");
		countries.put("ATA", "AQ");
		countries.put("ATG", "AG");
		countries.put("ARG", "AR");
		countries.put("ARM", "AM");
		countries.put("ABW", "AW");
		countries.put("AUS", "AU");
		countries.put("AUT", "AT");
		countries.put("AZE", "AZ");
		countries.put("BHS", "BS");
		countries.put("BHR", "BH");
		countries.put("BGD", "BD");
		countries.put("BRB", "BB");
		countries.put("BLR", "BY");
		countries.put("BEL", "BE");
		countries.put("BLZ", "BZ");
		countries.put("BEN", "BJ");
		countries.put("BMU", "BM");
		countries.put("BTN", "BT");
		countries.put("BOL", "BO");
		countries.put("BES", "BQ");
		countries.put("BIH", "BA");
		countries.put("BWA", "BW");
		countries.put("BVT", "BV");
		countries.put("BRA", "BR");
		countries.put("IOT", "IO");
		countries.put("BRN", "BN");
		countries.put("BGR", "BG");
		countries.put("BFA", "BF");
		countries.put("BDI", "BI");
		countries.put("CPV", "CV");
		countries.put("KHM", "KH");
		countries.put("CMR", "CM");
		countries.put("CAN", "CA");
		countries.put("CYM", "KY");
		countries.put("CAF", "CF");
		countries.put("TCD", "TD");
		countries.put("CHL", "CL");
		countries.put("CHN", "CN");
		countries.put("CXR", "CX");
		countries.put("CCK", "CC");
		countries.put("COL", "CO");
		countries.put("COM", "KM");
		countries.put("COG", "CG");
		countries.put("COD", "CD");
		countries.put("COK", "CK");
		countries.put("CRI", "CR");
		countries.put("CIV", "CI");
		countries.put("HRV", "HR");
		countries.put("CUB", "CU");
		countries.put("CUW", "CW");
		countries.put("CYP", "CY");
		countries.put("CZE", "CZ");
		countries.put("DNK", "DK");
		countries.put("DJI", "DJ");
		countries.put("DMA", "DM");
		countries.put("DOM", "DO");
		countries.put("ECU", "EC");
		countries.put("EGY", "EG");
		countries.put("SLV", "SV");
		countries.put("GNQ", "GQ");
		countries.put("ERI", "ER");
		countries.put("EST", "EE");
		countries.put("ETH", "ET");
		countries.put("FLK", "FK");
		countries.put("FRO", "FO");
		countries.put("FJI", "FJ");
		countries.put("FIN", "FI");
		countries.put("FRA", "FR");
		countries.put("GUF", "GF");
		countries.put("PYF", "PF");
		countries.put("ATF", "TF");
		countries.put("GAB", "GA");
		countries.put("GMB", "GM");
		countries.put("GEO", "GE");
		countries.put("D<<", "DE");
		countries.put("GHA", "GH");
		countries.put("GIB", "GI");
		countries.put("GRC", "GR");
		countries.put("GRL", "GL");
		countries.put("GRD", "GD");
		countries.put("GLP", "GP");
		countries.put("GUM", "GU");
		countries.put("GTM", "GT");
		countries.put("GGY", "GG");
		countries.put("GIN", "GN");
		countries.put("GNB", "GW");
		countries.put("GUY", "GY");
		countries.put("HTI", "HT");
		countries.put("HMD", "HM");
		countries.put("VAT", "VA");
		countries.put("HND", "HN");
		countries.put("HKG", "HK");
		countries.put("HUN", "HU");
		countries.put("ISL", "IS");
		countries.put("IND", "IN");
		countries.put("IDN", "ID");
		countries.put("IRN", "IR");
		countries.put("IRQ", "IQ");
		countries.put("IRL", "IE");
		countries.put("IMN", "IM");
		countries.put("ISR", "IL");
		countries.put("ITA", "IT");
		countries.put("JAM", "JM");
		countries.put("JPN", "JP");
		countries.put("JEY", "JE");
		countries.put("JOR", "JO");
		countries.put("KAZ", "KZ");
		countries.put("KEN", "KE");
		countries.put("KIR", "KI");
		countries.put("PRK", "KP");
		countries.put("KOR", "KR");
		countries.put("KWT", "KW");
		countries.put("KGZ", "KG");
		countries.put("LAO", "LA");
		countries.put("LVA", "LV");
		countries.put("LBN", "LB");
		countries.put("LSO", "LS");
		countries.put("LBR", "LR");
		countries.put("LBY", "LY");
		countries.put("LIE", "LI");
		countries.put("LTU", "LT");
		countries.put("LUX", "LU");
		countries.put("MAC", "MO");
		countries.put("MKD", "MK");
		countries.put("MDG", "MG");
		countries.put("MWI", "MW");
		countries.put("MYS", "MY");
		countries.put("MDV", "MV");
		countries.put("MLI", "ML");
		countries.put("MLT", "MT");
		countries.put("MHL", "MH");
		countries.put("MTQ", "MQ");
		countries.put("MRT", "MR");
		countries.put("MUS", "MU");
		countries.put("MYT", "YT");
		countries.put("MEX", "MX");
		countries.put("FSM", "FM");
		countries.put("MDA", "MD");
		countries.put("MCO", "MC");
		countries.put("MNG", "MN");
		countries.put("MNE", "ME");
		countries.put("MSR", "MS");
		countries.put("MAR", "MA");
		countries.put("MOZ", "MZ");
		countries.put("MMR", "MM");
		countries.put("NAM", "NA");
		countries.put("NRU", "NR");
		countries.put("NPL", "NP");
		countries.put("NLD", "NL");
		countries.put("NCL", "NC");
		countries.put("NZL", "NZ");
		countries.put("NIC", "NI");
		countries.put("NER", "NE");
		countries.put("NGA", "NG");
		countries.put("NIU", "NU");
		countries.put("NFK", "NF");
		countries.put("MNP", "MP");
		countries.put("NOR", "NO");
		countries.put("OMN", "OM");
		countries.put("PAK", "PK");
		countries.put("PLW", "PW");
		countries.put("PSE", "PS");
		countries.put("PAN", "PA");
		countries.put("PNG", "PG");
		countries.put("PRY", "PY");
		countries.put("PER", "PE");
		countries.put("PHL", "PH");
		countries.put("PCN", "PN");
		countries.put("POL", "PL");
		countries.put("PRT", "PT");
		countries.put("PRI", "PR");
		countries.put("QAT", "QA");
		countries.put("REU", "RE");
		countries.put("ROU", "RO");
		countries.put("RUS", "RU");
		countries.put("RWA", "RW");
		countries.put("BLM", "BL");
		countries.put("SHN", "SH");
		countries.put("KNA", "KN");
		countries.put("LCA", "LC");
		countries.put("MAF", "MF");
		countries.put("SPM", "PM");
		countries.put("VCT", "VC");
		countries.put("WSM", "WS");
		countries.put("SMR", "SM");
		countries.put("STP", "ST");
		countries.put("SAU", "SA");
		countries.put("SEN", "SN");
		countries.put("SRB", "RS");
		countries.put("SYC", "SC");
		countries.put("SLE", "SL");
		countries.put("SGP", "SG");
		countries.put("SXM", "SX");
		countries.put("SVK", "SK");
		countries.put("SVN", "SI");
		countries.put("SLB", "SB");
		countries.put("SOM", "SO");
		countries.put("ZAF", "ZA");
		countries.put("SGS", "GS");
		countries.put("SSD", "SS");
		countries.put("ESP", "ES");
		countries.put("LKA", "LK");
		countries.put("SDN", "SD");
		countries.put("SUR", "SR");
		countries.put("SJM", "SJ");
		countries.put("SWZ", "SZ");
		countries.put("SWE", "SE");
		countries.put("CHE", "CH");
		countries.put("SYR", "SY");
		countries.put("TWN", "TW");
		countries.put("TJK", "TJ");
		countries.put("TZA", "TZ");
		countries.put("THA", "TH");
		countries.put("TLS", "TL");
		countries.put("TGO", "TG");
		countries.put("TKL", "TK");
		countries.put("TON", "TO");
		countries.put("TTO", "TT");
		countries.put("TUN", "TN");
		countries.put("TUR", "TR");
		countries.put("TKM", "TM");
		countries.put("TCA", "TC");
		countries.put("TUV", "TV");
		countries.put("UGA", "UG");
		countries.put("UKR", "UA");
		countries.put("ARE", "AE");
		countries.put("GBR", "GB");
		countries.put("USA", "US");
		countries.put("UMI", "UM");
		countries.put("URY", "UY");
		countries.put("UZB", "UZ");
		countries.put("VUT", "VU");
		countries.put("VEN", "VE");
		countries.put("VNM", "VN");
		countries.put("VGB", "VG");
		countries.put("VIR", "VI");
		countries.put("WLF", "WF");
		countries.put("ESH", "EH");
		countries.put("YEM", "YE");
		countries.put("ZMB", "ZM");
		countries.put("ZWE", "ZW");
		return countries;
	}

	private static native int[] findCornerPoints(Bitmap bitmap);

	private static native Rect[][] binarizeAndFindCharacters(Bitmap in, Bitmap put);

	private static native String performRecognition(Bitmap bitmap, int numRows, int numCols, AssetManager assets);

	private static native void setYuvBitmapPixels(Bitmap bitmap, byte[] pixels);

	public static class Result {
		public static final int TYPE_PASSPORT = 1;
		public static final int TYPE_ID = 2;
		public static final int TYPE_INTERNAL_PASSPORT = 3;
		public static final int TYPE_DRIVER_LICENSE = 4;

		public static final int GENDER_MALE = 1;
		public static final int GENDER_FEMALE = 2;
		public static final int GENDER_UNKNOWN = 0;

		public int type;
		public String firstName;
		public String lastName;
		public String middleName;
		public String number;
		public int expiryYear, expiryMonth, expiryDay;
		public int birthYear, birthMonth, birthDay;
		public String issuingCountry;
		public String nationality;
		public int gender;
		public boolean doesNotExpire;
		public boolean mainCheckDigitIsValid;
		public String rawMRZ;
	}
}

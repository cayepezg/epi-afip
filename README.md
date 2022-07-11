1.- Chequear el campo file_stringbase64 no venga vacío

2.- Chequear que esté soportado el tipo de archivo

3.- Chequear que el archivo, tenga al menos un QR (Aplicar filtro de QR). 
	En caso de poseer más de un QR, aplicar los siguientes pasos para cada uno de ellos:

4.- Validar que la Informacíón del QR cumpla alguna de las siguietnes condiciones:

	4.1.- Que sea una URL y que esta tenga un parámetro llamado "p" o
	4.2.- Chequear que no sea una URL pero que tenga un valor distinto de vacío.
	
5.- Chequear que el valor extraído del punto anterior esté codificado en base64

6.- Decodificar el hash del punto anterior y verificar que sesa un JSON cuyo
	contenido corresponda con el formato AFIP. En el caso de coincidir, retornar 
	dicho valor y no chequear el resto de los QR (en el caso de que existan).
	
	
	
Ejemplo de factura AFIP:
	https://www.afip.gob.ar/fe/qr/documentos/30000000007_001_00010_00000094.pdf
	
Validacíón de campos AFIP:
	https://www.afip.gob.ar/fe/qr/especificaciones.asp
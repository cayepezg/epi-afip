package com.cyepez.servicios;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Base64;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.xml.bind.DatatypeConverter;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import com.google.zxing.multi.MultipleBarcodeReader;

/**
 * Procesa archivo de tipo imágenes y pdf, buscando en estos códigos QR en los
 * que a su vez trata de encontrar una url que contenga un parámetro hasheado
 * en Base64. Este parámetro debe corresponder al formato de una factura AFIP.
 * @author Carlos Yépez.
 */
public class ProcessFile {
	

	/**
	 * Recibe el archivo stringBase64 y el tipo de archivo que es enviado al 
	 * endpoint a través del body.
	 * @param stringBase64
	 * @param tipoArchivo
	 * @return
	 * @throws Exception
	 */
	public static AFIPObject procesarArchivo(String stringBase64, 
			String tipoArchivo)
			throws Exception {
		
		String stringFile = null;
		
		if (tipoArchivo.equalsIgnoreCase("image/jpeg") ||
				tipoArchivo.equalsIgnoreCase("image/png")) {
			// No hago nada puesto la recomendación PAE es procesar imágenes. 
			stringFile = stringBase64;
		} else if (tipoArchivo.equalsIgnoreCase("application/pdf")) {
			// Convertir a imagen puesto la recomendación PAE es
			// trabajar con imágenes.
			stringFile = pdfToImageStringBase64(stringBase64);
		} else {
			// Preguntar que decisión tomar puesto en el requerimiento se 
			// menciona que se recibirán o imágen o PDF.
		}
		
		return procesarImgB64(stringFile);
		
	}
	
	/**
	 * Extrae informacion AFIP de una imagen (En formato StringBase64) que debe 
	 * contener al menos un código QR que apunte a una URL la cual debe tener en
	 * un parámetro la información AFIP mencionada, codificada en BASE64.
	 * @param stringFile
	 * @return
	 * @throws Exception
	 */
	public static AFIPObject procesarImgB64(String stringFile) 
			throws Exception {
		
		AFIPObject afip = null;
		
		byte[] imageBytes = DatatypeConverter.parseBase64Binary(stringFile);
		
        BufferedImage barCodeBufferedImage = ImageIO.read(
        		new ByteArrayInputStream(imageBytes));

        LuminanceSource source = 
        		new BufferedImageLuminanceSource(barCodeBufferedImage);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        Reader reader = new MultiFormatReader();
        MultipleBarcodeReader bcReader = 
        		new GenericMultipleBarcodeReader(reader);
        Hashtable<DecodeHintType, Object> hints =
        		new Hashtable<DecodeHintType, Object>();
        
        // Verificar en esta linea si sirve para filtrar los únicamente
        // los código QR.
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

        for (Result result : bcReader.decodeMultiple(bitmap, hints)) {
        	Map<String, String> map = getURIQueryParamans(result.getText());
        	
        	if (map.get("p")!= null) {
        		afip = new ObjectMapper().readValue(
        				new String(Base64.getDecoder().decode(map.get("p"))), 
        				AFIPObject.class);
        	}
        	
        }
        return afip;
	}
	
	/**
	 * Dada una URL, retorna un mapa de parámetros presente en esta.
	 * @param url
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public static Map<String, String> getURIQueryParamans(String url) 
			throws UnsupportedEncodingException{
		HashMap<String, String> map = new HashMap<String, String>();
		
		String query;
		try {
			query = new URL(url).getQuery();
			if (query == null || query.isEmpty()) {
				return map;
			}
			
			for (String param : query.split("&")) {
				String[] pair = param.split("=");
				String key = URLDecoder.decode(pair[0], "UTF-8");
				if (pair.length > 1) {
					map.put(key, URLDecoder.decode(pair[1], "UTF-8"));
				}
			}
			
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		
		return map;
	}
	
	
	/**
	 * Transforma un PDF en formato StringBase64 a imagen StringBase64
	 * @param pdfBase64
	 * @return StringBase64
	 */
	public static String pdfToImageStringBase64(String pdfBase64) {
		
		final ByteArrayOutputStream os = new ByteArrayOutputStream();
		String result = null;
		PDDocument doc = null;
		
		byte[] pdfBytes = DatatypeConverter.parseBase64Binary(pdfBase64);
		
		try {
			doc = PDDocument.load(new ByteArrayInputStream(pdfBytes));
			@SuppressWarnings("unchecked")
			List<PDPage> list = doc.getDocumentCatalog().getAllPages();
			
			for (PDPage p: list) {
				BufferedImage image = p.convertToImage();
				ImageIO.write(image, "png", os);
				result = Base64.getEncoder().encodeToString(os.toByteArray());
			}
			doc.close();
			os.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		} 

		return result;
	}
	
	
	/**
	 * Clase asociada a la estructura AFIP indicada por PAE.
	 * @author Carlos Yepez.
	 */
	public static class AFIPObject implements Serializable {

		private static final long serialVersionUID = 1L;
		
		private String ver;
		
		// formato YYYY-mm-dd
		private String fecha;
		
		private Long cuit;
		
		private Integer ptoVta;

		private Integer tipoCmp;
		
		private Integer nroCmp;

		private Float importe;
		
		private String moneda;
		
		private Float ctz;
		
		private Integer tipoDocRec;
		
		private Long nroDocRec;
		
		private String tipoCodAut;
		
		private Long codAut;

		/**
		 * Constructor por defecto
		 */
		public AFIPObject() {
			super();
		}

		/**
		 * Constructor con todos los parámetros.
		 * @param ver
		 * @param fecha
		 * @param cuit
		 * @param ptoVta
		 * @param tipoCmp
		 * @param nroCmp
		 * @param importe
		 * @param moneda
		 * @param ctz
		 * @param tipoDocRec
		 * @param nroDocRec
		 * @param tipoCodAut
		 * @param codAut
		 */
		public AFIPObject(String ver, String fecha, Long cuit, Integer ptoVta,
				Integer tipoCmp, Integer nroCmp, Float importe, String moneda,
				Float ctz, Integer tipoDocRec, Long nroDocRec, 
				String tipoCodAut, Long codAut) {
			super();
			this.ver = ver;
			this.fecha = fecha;
			this.cuit = cuit;
			this.ptoVta = ptoVta;
			this.tipoCmp = tipoCmp;
			this.nroCmp = nroCmp;
			this.importe = importe;
			this.moneda = moneda;
			this.ctz = ctz;
			this.tipoDocRec = tipoDocRec;
			this.nroDocRec = nroDocRec;
			this.tipoCodAut = tipoCodAut;
			this.codAut = codAut;
		}

		// Getters y Setters
		
		public String getVer() {
			return ver;
		}

		public void setVer(String ver) {
			this.ver = ver;
		}

		public String getFecha() {
			return fecha;
		}

		public void setFecha(String fecha) {
			this.fecha = fecha;
		}

		public Long getCuit() {
			return cuit;
		}

		public void setCuit(Long cuit) {
			this.cuit = cuit;
		}

		public Integer getPtoVta() {
			return ptoVta;
		}

		public void setPtoVta(Integer ptoVta) {
			this.ptoVta = ptoVta;
		}

		public Integer getTipoCmp() {
			return tipoCmp;
		}

		public void setTipoCmp(Integer tipoCmp) {
			this.tipoCmp = tipoCmp;
		}

		public Integer getNroCmp() {
			return nroCmp;
		}

		public void setNroCmp(Integer nroCmp) {
			this.nroCmp = nroCmp;
		}

		public Float getImporte() {
			return importe;
		}

		public void setImporte(Float importe) {
			this.importe = importe;
		}

		public String getMoneda() {
			return moneda;
		}

		public void setMoneda(String moneda) {
			this.moneda = moneda;
		}

		public Float getCtz() {
			return ctz;
		}

		public void setCtz(Float ctz) {
			this.ctz = ctz;
		}

		public Integer getTipoDocRec() {
			return tipoDocRec;
		}

		public void setTipoDocRec(Integer tipoDocRec) {
			this.tipoDocRec = tipoDocRec;
		}

		public Long getNroDocRec() {
			return nroDocRec;
		}

		public void setNroDocRec(Long nroDocRec) {
			this.nroDocRec = nroDocRec;
		}

		public String getTipoCodAut() {
			return tipoCodAut;
		}

		public void setTipoCodAut(String tipoCodAut) {
			this.tipoCodAut = tipoCodAut;
		}

		public Long getCodAut() {
			return codAut;
		}

		public void setCodAut(Long codAut) {
			this.codAut = codAut;
		}
		
	}

}

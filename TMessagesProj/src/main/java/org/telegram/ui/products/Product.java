package org.telegram.ui.products;

public class Product {

    String productId;
    String productTitle;
    String productDesc;
    String idUser;


    public Product(){

    }

    public Product(String productId, String productName, String productDesc, String idUser) {
        this.productTitle = productName;
        this.productDesc = productDesc;
        this.productId = productId;
        this.idUser = idUser;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public void setProductTitle(String productTitle) {
        this.productTitle = productTitle;
    }

    public void setProductDesc(String productDesc) {
        this.productDesc = productDesc;
    }

    public String getIdUser() {
        return idUser;
    }

    public void setIdUser(String idUser) {
        this.idUser = idUser;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductTitle() {
        return productTitle;
    }

    public String getProductDesc() {
        return productDesc;
    }
}


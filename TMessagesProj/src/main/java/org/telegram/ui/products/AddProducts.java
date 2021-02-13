package org.telegram.ui.products;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

public class AddProducts extends Activity {


    EditText edtDesc;
    Button btnSave;
    Spinner spinnerTitle;

    // text data
    FirebaseDatabase firebaseDatabase;
    DatabaseReference databaseProducts;

    String title;


    private int currentAccount = UserConfig.selectedAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_products);

        setTitle("Add a Product");

        // text
        firebaseDatabase = FirebaseDatabase.getInstance();
        databaseProducts = firebaseDatabase.getReference("products");

        edtDesc = findViewById(R.id.edt_desc);
        btnSave = findViewById(R.id.btn_save_product);
        spinnerTitle = findViewById(R.id.spinnerTitleType);
//        spinnerTitle.setOnItemSelectedListener(this);
//        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
//                android.R.layout.simple_spinner_item, R.array.title);
//        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//        spinnerTitle.setAdapter(adapter);

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveDataToFirebase();
                finish();
            }
        });


    }

    //    @Override
//    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
//         title = (String) spinnerTitle.getSelectedItem();
//    }
//
//    @Override
//    public void onNothingSelected(AdapterView<?> adapterView) {
//
//    }
    private void saveDataToFirebase() {
        //write a message to the database

        String title = spinnerTitle.getSelectedItem().toString();
        String desc = edtDesc.getText().toString().trim();

        String idUser = String.valueOf(UserConfig.getInstance(currentAccount).getClientUserId());

        if (!TextUtils.isEmpty(desc)) {
            String id = databaseProducts.push().getKey();

            Product product = new Product(id, title, desc, idUser);

            databaseProducts.child(id).setValue(product);

            Toast.makeText(this, "Product Advertise Added", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Product Advertise not Added", Toast.LENGTH_LONG).show();
        }

    }
}


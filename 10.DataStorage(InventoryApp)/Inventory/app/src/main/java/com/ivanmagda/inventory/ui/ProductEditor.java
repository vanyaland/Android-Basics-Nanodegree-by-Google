/**
 * Copyright (c) 2016 Ivan Magda
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.ivanmagda.inventory.ui;

import android.app.LoaderManager;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.ivanmagda.inventory.R;
import com.ivanmagda.inventory.model.data.ProductContract.ProductEntry;
import com.ivanmagda.inventory.model.object.Product;
import com.ivanmagda.inventory.util.ProductUtils;

import static com.ivanmagda.inventory.ui.ProductEditor.EditorActivityMode.CREATE_NEW;
import static com.ivanmagda.inventory.ui.ProductEditor.EditorActivityMode.EDIT;

public class ProductEditor extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    enum EditorActivityMode {EDIT, CREATE_NEW}

    private static final String LOG_TAG = ProductEditor.class.getSimpleName();
    private static final int PRODUCT_LOADER = 1;

    /**
     * Helps to understand in what mode activity started.
     */
    private EditorActivityMode mActivityMode = CREATE_NEW;

    /**
     * Uri of the current editing product or null if add one.
     */
    private Uri mCurrentProductUri;

    /**
     * EditText field to enter the products's name.
     */
    private EditText mNameEditText;

    /**
     * EditText field to enter the products's price.
     */
    private EditText mPriceEditText;

    /**
     * EditText field to enter the products's quantity.
     */
    private EditText mQuantityEditText;

    /**
     * EditText field to enter the products's supplier email address.
     */
    private EditText mSupplierEmailEditText;

    /**
     * TextView to show the current products's sale info.
     */
    private TextView mSoldQuantityTextView;

    /**
     * TextView to show the current products's receive shipment info.
     */
    private TextView mReceiveQuantityTextView;

    private boolean mProductHasChanged = false;
    private View.OnTouchListener mTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            mProductHasChanged = true;
            return false;
        }
    };

    /**
     * Currently editing product.
     * Used only when {@link ProductEditor} in the edit mode {@link #mActivityMode}.
     */
    private Product mProduct;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_editor);
        configure();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_product_editor, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (mActivityMode == CREATE_NEW) {
            menu.findItem(R.id.action_delete).setVisible(false);
            menu.findItem(R.id.action_order).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_save:
                saveProduct();
                finish();
                return true;
            case R.id.action_delete:
                showDeleteConfirmationDialog();
                return true;
            case R.id.action_order:
                // TODO: implement order behaviour.
                return true;
            case android.R.id.home:
                if (!mProductHasChanged) {
                    NavUtils.navigateUpFromSameTask(ProductEditor.this);
                    return true;
                }

                showUnsavedChangesDialog(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        NavUtils.navigateUpFromSameTask(ProductEditor.this);
                    }
                });

                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (!mProductHasChanged) {
            super.onBackPressed();
            return;
        }
        showUnsavedChangesDialog(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
    }

    /**
     * Private configure helper methods.
     */

    private void configure() {
        mCurrentProductUri = getIntent().getData();
        mActivityMode = (mCurrentProductUri == null ? CREATE_NEW : EDIT);

        findViews();
        setListeners();

        if (mActivityMode == CREATE_NEW) {
            setTitle(R.string.editor_activity_title_new_product);
            findViewById(R.id.container_track_sale).setVisibility(View.GONE);
            findViewById(R.id.container_receive_shipment).setVisibility(View.GONE);
            invalidateOptionsMenu();
        } else {
            setTitle(R.string.editor_activity_title_edit_product);
            mQuantityEditText.setKeyListener(null);
            mQuantityEditText.setEnabled(false);

            getLoaderManager().initLoader(PRODUCT_LOADER, null, this);
        }
    }

    private void findViews() {
        mNameEditText = (EditText) findViewById(R.id.edit_product_name);
        mPriceEditText = (EditText) findViewById(R.id.edit_product_price);
        mQuantityEditText = (EditText) findViewById(R.id.edit_product_quantity);
        mSupplierEmailEditText = (EditText) findViewById(R.id.edit_product_supplier);
        mSoldQuantityTextView = (TextView) findViewById(R.id.sold_quantity_text_view);
        mReceiveQuantityTextView = (TextView) findViewById(R.id.receive_quantity_text_view);
    }

    private void setListeners() {
        mNameEditText.setOnTouchListener(mTouchListener);
        mPriceEditText.setOnTouchListener(mTouchListener);
        mQuantityEditText.setOnTouchListener(mTouchListener);
        mSupplierEmailEditText.setOnTouchListener(mTouchListener);
        mSoldQuantityTextView.setOnTouchListener(mTouchListener);
        mReceiveQuantityTextView.setOnTouchListener(mTouchListener);

        findViewById(R.id.decrement_sale_button).setOnTouchListener(mTouchListener);
        findViewById(R.id.increment_sale_button).setOnTouchListener(mTouchListener);
        findViewById(R.id.decrement_receive_button).setOnTouchListener(mTouchListener);
        findViewById(R.id.increment_receive_button).setOnTouchListener(mTouchListener);
    }

    /**
     * Implement LoaderManager.LoaderCallbacks<Cursor>.
     */

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(this, mCurrentProductUri, ProductEntry.PROJECTION_ALL, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (!cursor.moveToFirst()) return;

        mProduct = ProductUtils.fromCursor(cursor);
        updateUiWithProduct(mProduct);
    }

    private void updateUiWithProduct(Product product) {
        mNameEditText.setText(product.getName());
        mPriceEditText.setText(String.valueOf(product.getPrice()));
        mQuantityEditText.setText(String.valueOf(product.getQuantity()));
        mSupplierEmailEditText.setText(product.getSupplier());
        mSoldQuantityTextView.setText(String.valueOf(product.getSoldQuantity()));
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mProduct = null;
    }

    /**
     * Helper public method for tracking product sale and receive quantities.
     *
     * @param button pressed button.
     */
    public void trackQuantityButtonPressed(View button) {
        int id = button.getId();
        switch (id) {
            case R.id.decrement_sale_button:
                updateSaleQuantityTextView(mProduct.decrementSaleQuantity());
                break;
            case R.id.increment_sale_button:
                updateSaleQuantityTextView(mProduct.incrementSaleQuantity());
                break;
            case R.id.decrement_receive_button:
                updateReceiveQuantityTextView(mProduct.decrementReceiveQuantity());
                break;
            case R.id.increment_receive_button:
                updateReceiveQuantityTextView(mProduct.incrementReceiveQuantity());
                break;
            default:
                throw new IllegalArgumentException("Unsupported button pressed with id: " + id);
        }

        mQuantityEditText.setText(String.valueOf(mProduct.getQuantity()));
    }

    private void updateSaleQuantityTextView(int value) {
        mSoldQuantityTextView.setText(String.valueOf(value));
    }

    private void updateReceiveQuantityTextView(int value) {
        mReceiveQuantityTextView.setText(String.valueOf(value));
    }

    /**
     * Private helper methods for working with the database.
     */

    private void saveProduct() {
        ContentValues values = readFromInputs();
        switch (mActivityMode) {
            case CREATE_NEW:
                Uri newRowUri = getContentResolver().insert(ProductEntry.CONTENT_URI, values);
                if (newRowUri == null)
                    Toast.makeText(this, R.string.editor_insert_product_failed, Toast.LENGTH_SHORT).show();
                else
                    Toast.makeText(this, R.string.editor_insert_product_successful, Toast.LENGTH_SHORT).show();
                break;
            case EDIT:
                int updatedRows = getContentResolver().update(mCurrentProductUri, values, null, null);
                if (updatedRows == 1)
                    Toast.makeText(this, R.string.editor_edit_product_successful, Toast.LENGTH_SHORT).show();
                else
                    Toast.makeText(this, R.string.editor_edit_product_failed, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private ContentValues readFromInputs() {
        // Read from input text fields.
        String name = mNameEditText.getText().toString().trim();
        String supplier = mSupplierEmailEditText.getText().toString().trim();
        String priceString = mPriceEditText.getText().toString().trim();
        double price = 0;
        String quantityString = mQuantityEditText.getText().toString().trim();
        int quantity = 0;

        // TODO: put picture.
        byte[] picture = null;

        int soldQuantity = 0;
        if (mActivityMode == EDIT) {
            assert mProduct != null;
            soldQuantity = mProduct.getSoldQuantity();
        } else {
            String soldString = mSoldQuantityTextView.getText().toString();
            if (!TextUtils.isEmpty(soldString)) soldQuantity = Integer.parseInt(soldString);
        }

        if (!TextUtils.isEmpty(priceString)) price = Double.parseDouble(priceString);
        if (!TextUtils.isEmpty(quantityString)) quantity = Integer.parseInt(quantityString);

        ContentValues values = new ContentValues();
        values.put(ProductEntry.COLUMN_PRODUCT_NAME, name);
        values.put(ProductEntry.COLUMN_PRODUCT_PRICE, price);
        values.put(ProductEntry.COLUMN_PRODUCT_QUANTITY, quantity);
        values.put(ProductEntry.COLUMN_PRODUCT_SOLD_QUANTITY, soldQuantity);
        values.put(ProductEntry.COLUMN_PRODUCT_SUPPLIER, supplier);
        values.put(ProductEntry.COLUMN_PRODUCT_PICTURE, picture);

        return values;
    }

    /**
     * Perform the deletion of the product in the database.
     */
    private void deleteProduct() {
        if (mActivityMode == CREATE_NEW) {
            Toast.makeText(this, R.string.editor_delete_product_failed, Toast.LENGTH_SHORT).show();
        } else {
            int rowsDeleted = getContentResolver().delete(mCurrentProductUri, null, null);
            if (rowsDeleted == 0)
                Toast.makeText(this, R.string.editor_delete_product_failed, Toast.LENGTH_SHORT).show();
            else
                Toast.makeText(this, R.string.editor_delete_product_successful, Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    /**
     * Build and show confirmation dialogs.
     */

    private void showUnsavedChangesDialog(DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.unsaved_changes_dialog_msg);
        builder.setPositiveButton(R.string.discard, listener);
        builder.setNegativeButton(R.string.keep_editing, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (dialog != null) dialog.dismiss();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void showDeleteConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.delete_dialog_msg);
        builder.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                deleteProduct();
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                if (dialog != null) dialog.dismiss();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

}

package com.example.maliy.cars;

import com.amazonaws.mobile.auth.core.IdentityHandler;
import com.amazonaws.mobile.auth.core.IdentityManager;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapperConfig;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBQueryExpression;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.PaginatedList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.gson.Gson;

/**
 * Created by maliy on 24/02/2018.
 */

public class DBHelper {

    DynamoDBMapper dynamoDBMapper;
    IdentityManager identityManager;
    final String[] userId = new String[1];

    public DBHelper(DynamoDBMapper dynamoDBMapper, IdentityManager identityManager) {
        this.dynamoDBMapper = dynamoDBMapper;
        this.identityManager = identityManager;

        IdentityManager.getDefaultIdentityManager().getUserID(new IdentityHandler() {
            @Override
            public void onIdentityId(String s) {
                System.out.println(s);
                userId[0] = s;
            }

            @Override
            public void handleError(Exception e) {
                System.out.println(e);
            }
        });

    }

    /** CURD func **/

    public void createNews(String email, String pass, int vehicleRegistrationNum) {
        final UsersDO newsItem = new UsersDO();
        //String userId = identityManager.getCachedUserID();

        newsItem.setUserId(userId[0]);
        newsItem.setPassword(pass);
        newsItem.setEmail(email);
        newsItem.setVehicleRegistrationNum(vehicleRegistrationNum);

        new Thread(new Runnable() {
            @Override
            public void run() {
                dynamoDBMapper.save(newsItem);
                // Item saved
            }
        }).start();
    }

    public boolean readNews(final String email, final String pass) {
        final boolean[] exist = {false};
        new Thread(new Runnable() {
            @Override
            public void run() {

                DynamoDBMapperConfig config = new DynamoDBMapperConfig(DynamoDBMapperConfig.ConsistentReads.CONSISTENT);
                UsersDO newsItem = dynamoDBMapper.load(UsersDO.class,userId[0]);
                if(newsItem != null)
                    exist[0] = true;
                // Item read
                // Log.d("News Item:", newsItem.toString());
            }
        }).start();
        return exist[0];
    }

    public void updateNews(int vehicleRegistrationNum) {
        final UsersDO newsItem = new UsersDO();

        newsItem.setUserId(identityManager.getCachedUserID());

        newsItem.setVehicleRegistrationNum(vehicleRegistrationNum);


        new Thread(new Runnable() {
            @Override
            public void run() {

                dynamoDBMapper.save(newsItem);

                // Item updated
            }
        }).start();
    }

    public void deleteNews(final String email) {
        new Thread(new Runnable() {
            @Override
            public void run() {

                UsersDO newsItem = new UsersDO();

                newsItem.setUserId(identityManager.getCachedUserID());    //partition key

                newsItem.setEmail(email);  //range (sort) key

                dynamoDBMapper.delete(newsItem);

                // Item deleted
            }
        }).start();
    }

    public void queryNote(final String email) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                UsersDO note = new UsersDO();
                note.setUserId(identityManager.getCachedUserID());
                note.setEmail(email);

                Condition rangeKeyCondition = new Condition()
                        .withComparisonOperator(ComparisonOperator.BEGINS_WITH)
                        .withAttributeValueList(new AttributeValue().withS("Trial"));

                DynamoDBQueryExpression queryExpression = new DynamoDBQueryExpression()
                        .withHashKeyValues(note)
                        .withRangeKeyCondition("articleId", rangeKeyCondition)
                        .withConsistentRead(false);

                PaginatedList<UsersDO> result = dynamoDBMapper.query(UsersDO.class, queryExpression);

                Gson gson = new Gson();
                StringBuilder stringBuilder = new StringBuilder();

                // Loop through query results
                for (int i = 0; i < result.size(); i++) {
                    String jsonFormOfItem = gson.toJson(result.get(i));
                    stringBuilder.append(jsonFormOfItem + "\n\n");
                }

                // Add your code here to deal with the data result
                //updateOutput(stringBuilder.toString());

                if (result.isEmpty()) {
                    // There were no items matching your query.
                }
            }
        }).start();
    }

}

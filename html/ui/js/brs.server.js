/**
 * @depends {brs.js}
 */
var BRS = (function(BRS, $, undefined) {
    var _password;

    BRS.multiQueue = null;

    BRS.setServerPassword = function(password) {
        _password = password;
    };

    BRS.getServerPassword = function() {
        return _password
    }

    BRS.sendOutsideRequest = function(url, data, callback, async) {
        if ($.isFunction(data)) {
            async = callback;
            callback = data;
            data = {};
        } else {
            data = data || {};
        }

        $.support.cors = true;

        $.ajax({
            url: url,
            crossDomain: true,
            dataType: "json",
            type: "GET",
            timeout: 30000,
            async: (async === undefined ? true : async),
            data: data
        }).done(function(json) {
            //why is this necessary??..
            if (json.errorCode && !json.errorDescription) {
                json.errorDescription = (json.errorMessage ? json.errorMessage : $.t("server_error_unknown"));
            }
            if (callback) {
                callback(json, data);
            }
        }).fail(function(xhr, textStatus, error) {
            if (callback) {
                callback({
                    "errorCode": -1,
                    "errorDescription": error
                }, {});
            }
        });
    };

    BRS.sendRequest = function(requestType, data, callback, async) {
        if (requestType === undefined) {
            return;
        }

        if ($.isFunction(data)) {
            async = callback;
            callback = data;
            data = {};
        } else {
            data = data || {};
        }

        $.each(data, function(key, val) {
            if (key != "secretPhrase") {
                if (typeof val == "string") {
                    data[key] = $.trim(val);
                }
            }
        });

        //convert NXT to NQT...
        try {
            var nxtFields = ["feeNXT", "amountNXT", "priceNXT", "refundNXT", "discountNXT", "minActivationAmountNXT"];

            for (var i = 0; i < nxtFields.length; i++) {
                var nxtField = nxtFields[i];
                var field = nxtField.replace("NXT", "");

                if (nxtField in data) {
                    data[field + "NQT"] = BRS.convertToNQT(data[nxtField]);
                    delete data[nxtField];
                }
            }
        } catch (err) {
            if (callback) {
                callback({
                    "errorCode": 1,
                    "errorDescription": err + " (Field: " + field + ")"
                });
            }

            return;
        }

        if (!data.recipientPublicKey) {
            delete data.recipientPublicKey;
        }
        if (!data.referencedTransactionFullHash) {
            delete data.referencedTransactionFullHash;
        }

        //gets account id from passphrase client side, used only for login.
        if (requestType == "getAccountId") {
            var accountId = BRS.getAccountId(data.secretPhrase);

            var nxtAddress = new NxtAddress();
            var accountRS;

            if (nxtAddress.set(accountId)) {
                accountRS = nxtAddress.toString();
            } else {
                accountRS = "";
            }

            if (callback) {
                callback({
                    "account": accountId,
                    "accountRS": accountRS
                });
            }
            return;
        }

        //check to see if secretPhrase supplied matches logged in account, if not - show error.
        if ("secretPhrase" in data) {
            accountId = BRS.getAccountId(BRS.rememberPassword ? _password : data.secretPhrase);
            if (accountId != BRS.account) {
                if (callback) {
                    callback({
                        "errorCode": 1,
                        "errorDescription": $.t("error_passphrase_incorrect")
                    });
                }
                return;
            } else {
                //ok, accountId matches..continue with the real request.
                BRS.processAjaxRequest(requestType, data, callback, async);
            }
        } else {
            BRS.processAjaxRequest(requestType, data, callback, async);
        }
    };

    BRS.processAjaxRequest = function(requestType, data, callback, async) {
        if (!BRS.multiQueue) {
            BRS.multiQueue = $.ajaxMultiQueue(8);
        }
        var extra;
        if (data._extra) {
            extra = data._extra;
            delete data._extra;
        } else {
            extra = null;
        }

        var currentPage = null;
        var currentSubPage = null;

        //means it is a page request, not a global request.. Page requests can be aborted.
        if (requestType.slice(-1) == "+") {
            requestType = requestType.slice(0, -1);
            currentPage = BRS.currentPage;
        } else {
            //not really necessary... we can just use the above code..
            var plusCharacter = requestType.indexOf("+");

            if (plusCharacter > 0) {
                var subType = requestType.substr(plusCharacter);
                requestType = requestType.substr(0, plusCharacter);
                currentPage = BRS.currentPage;
            }
        }

        if (currentPage && BRS.currentSubPage) {
            currentSubPage = BRS.currentSubPage;
        }

        var type = (("secretPhrase" in data) || (data.broadcast == "false") ) ? "POST" : "GET";
        var url = BRS.server + "/burst?requestType=" + requestType;

        if (type == "GET") {
            // rico666: gives us lots (thousands) of connection refused messages in the UI
            // has been there for ages, no clear function visible
            //   if (typeof data == "string") {
            //	data += "&random=" + Math.random();
            //    }
            //    else {
            data._ = $.now();
            //    }
        }

        var secretPhrase = "";

        //unknown account..
        if (type == "POST" && (BRS.accountInfo.errorCode && BRS.accountInfo.errorCode == 5)) {
            if (callback) {
                callback({
                    "errorCode": 2,
                    "errorDescription": $.t("error_new_account")
                }, data);
            } else {
                $.notify($.t("error_new_account"), {
                    type: 'danger',
                    offset: {
                        x: 5,
                        y: 60
                        }
                });
            }
            return;
        }

        if (data.referencedTransactionFullHash) {
            if (!/^[a-z0-9]{64}$/.test(data.referencedTransactionFullHash)) {
                if (callback) {
                    callback({
                        "errorCode": -1,
                        "errorDescription": $.t("error_invalid_referenced_transaction_hash")
                    }, data);
                } else {
                    $.notify($.t("error_invalid_referenced_transaction_hash"), {
                        type: 'danger',
                        offset: {
                            x: 5,
                            y: 60
                            }
                    });
                }
                return;
            }
        }

        if (!BRS.isLocalHost && type == "POST") {
            if (BRS.rememberPassword) {
                secretPhrase = _password;
            } else {
                secretPhrase = data.secretPhrase;
            }

            delete data.secretPhrase;

            if (BRS.accountInfo && BRS.accountInfo.publicKey) {
                data.publicKey = BRS.accountInfo.publicKey;
            } else {
                data.publicKey = BRS.generatePublicKey(secretPhrase);
                BRS.accountInfo.publicKey = data.publicKey;
            }
        } else if (type == "POST" && BRS.rememberPassword) {
            data.secretPhrase = _password;
        }

        $.support.cors = true;

        if (type == "GET") {
            var ajaxCall = BRS.multiQueue.queue;
        } else {
            var ajaxCall = $.ajax;
        }

        // the recipient param is used to do some message encryption ... so I can not remove it as I thought first
        requestTypeWithNonWalletCompatibleRecipientParam = ["dgsPurchase", "dgsRefund", "dgsDelivery", "dgsFeedback", "buyAlias"];
        if ( requestTypeWithNonWalletCompatibleRecipientParam.indexOf(requestType) + 1 ) {
            delete data.recipient;
        }

        //workaround for 1 specific case.. ugly
        if (data.querystring) {
            data = data.querystring;
            type = "POST";
        }

        if (requestType == "broadcastTransaction") {
            type = "POST";
        }
        async = (async === undefined ? true : async);
        if (async === false && type == "GET") {
            url += "&" + $.param(data);
            var client = new XMLHttpRequest();
            client.open("GET", url, false);
            client.setRequestHeader("Content-Type", "text/plain;charset=UTF-8");
            client.data = data;
            client.send();
            var response = JSON.parse(client.responseText);
            callback(response, data);
        } else {
            ajaxCall({
                url: url,
                crossDomain: true,
                dataType: "json",
                type: type,
                timeout: 30000,
                async: true,
                currentPage: currentPage,
                currentSubPage: currentSubPage,
                shouldRetry: (type == "GET" ? 2 : undefined),
                data: data
            }).done(function(response, status, xhr) {
                if (BRS.console) {
                    BRS.addToConsole(this.url, this.type, this.data, response);
                }

                if (typeof data == "object" && "recipient" in data) {
                  var address = new NxtAddress();
                    if (/^BURST\-/i.test(data.recipient)) {
                        data.recipientRS = data.recipient;

                        if (address.set(data.recipient)) {
                            data.recipient = address.account_id();
                        }
                    } else {

                        if (address.set(data.recipient)) {
                            data.recipientRS = address.toString();
                        }
                    }
                }

                if (secretPhrase && response.unsignedTransactionBytes && !response.errorCode && !response.error) {
                    var publicKey = BRS.generatePublicKey(secretPhrase);
                    var signature = BRS.signBytes(response.unsignedTransactionBytes, converters.stringToHexString(secretPhrase));

                    if (!BRS.verifyBytes(signature, response.unsignedTransactionBytes, publicKey)) {
                        if (callback) {
                            callback({
                                "errorCode": 1,
                                "errorDescription": $.t("error_signature_verification_client")
                            }, data);
                        } else {
                            $.notify($.t("error_signature_verification_client"), {
                                type: 'danger',
                                offset: {
                                    x: 5,
                                    y: 60
                                    }
                            });
                        }
                        return;
                    } else {
                        var payload = BRS.verifyAndSignTransactionBytes(response.unsignedTransactionBytes, signature, requestType, data);

                        if (!payload) {
                            if (callback) {
                                callback({
                                    "errorCode": 1,
                                    "errorDescription": $.t("error_signature_verification_server")
                                }, data);
                            } else {
                                $.notify($.t("error_signature_verification_server"), {
                                    type: 'danger',
                                    offset: {
                                        x: 5,
                                        y: 60
                                        }
                                });
                            }
                            return;
                        } else {
                            if (data.broadcast == "false") {
                                response.transactionBytes = payload;
                                BRS.showRawTransactionModal(response);
                            } else {
                                if (callback) {
                                    if (extra) {
                                        data._extra = extra;
                                    }

                                    BRS.broadcastTransactionBytes(payload, callback, response, data);
                                } else {
                                    BRS.broadcastTransactionBytes(payload, null, response, data);
                                }
                            }
                        }
                    }
                } else {
                    if (response.errorCode || response.errorDescription || response.errorMessage || response.error) {
                        response.errorDescription = BRS.translateServerError(response);
                        delete response.fullHash;
                        if (!response.errorCode) {
                            response.errorCode = -1;
                        }
                    }

                    /*
		  if (response.errorCode && !response.errorDescription) {
		  response.errorDescription = (response.errorMessage ? response.errorMessage : $.t("error_unknown"));
		  }
                  else if (response.error && !response.errorDescription) {
		  response.errorDescription = (typeof response.error == "string" ? response.error : $.t("error_unknown"));
		  if (!response.errorCode) {
		  response.errorCode = 1;
		  }
		  }
		*/

                    if (response.broadcasted === false) {
                        BRS.showRawTransactionModal(response);
                    } else {
                        if (callback) {
                            if (extra) {
                                data._extra = extra;
                            }
                            callback(response, data);
                        }
                        if (data.referencedTransactionFullHash && !response.errorCode) {
                            $.notify($.t("info_referenced_transaction_hash"), {
                                type: 'info',
                                offset: {
                                       x: 5,
                                       y: 60
                                        }
                            });
                        }
                    }
                }
            }).fail(function(xhr, textStatus, error) {
                if (BRS.console) {
                    BRS.addToConsole(this.url, this.type, this.data, error, true);
                }

                if ((error == "error" || textStatus == "error") && (xhr.status == 404 || xhr.status === 0)) {
                    if (type == "POST") {
                        $.notify($.t("error_server_connect"), {
                            type: 'danger',
                            offset: 10
                        });
                    }
                }

                if (error == "abort") {
                    return;
                } else if (callback) {
                    if (error == "timeout") {
                        error = $.t("error_request_timeout");
                    }
                    callback({
                        "errorCode": -1,
                        "errorDescription": error
                    }, {});
                }
            });
        }
    };
    BRS.verifyAndSignTransactionBytes = function(transactionBytes, signature, requestType, data) {
        var transaction = {};
        var pos;
        var byteArray = converters.hexStringToByteArray(transactionBytes);

        transaction.type = byteArray[0];

        transaction.version = (byteArray[1] & 0xF0) >> 4;
        transaction.subtype = byteArray[1] & 0x0F;

        transaction.timestamp = String(converters.byteArrayToSignedInt32(byteArray, 2));
        transaction.deadline = String(converters.byteArrayToSignedShort(byteArray, 6));
        transaction.publicKey = converters.byteArrayToHexString(byteArray.slice(8, 40));
        transaction.recipient = String(converters.byteArrayToBigInteger(byteArray, 40));
        transaction.amountNQT = String(converters.byteArrayToBigInteger(byteArray, 48));
        transaction.feeNQT = String(converters.byteArrayToBigInteger(byteArray, 56));

        var refHash = byteArray.slice(64, 96);
        transaction.referencedTransactionFullHash = converters.byteArrayToHexString(refHash);
        if (transaction.referencedTransactionFullHash == "0000000000000000000000000000000000000000000000000000000000000000") {
            transaction.referencedTransactionFullHash = "";
        }
        //transaction.referencedTransactionId = converters.byteArrayToBigInteger([refHash[7], refHash[6], refHash[5], refHash[4], refHash[3], refHash[2], refHash[1], refHash[0]], 0);

        transaction.flags = 0;

        if (transaction.version > 0) {
            transaction.flags = converters.byteArrayToSignedInt32(byteArray, 160);
            transaction.ecBlockHeight = String(converters.byteArrayToSignedInt32(byteArray, 164));
            transaction.ecBlockId = String(converters.byteArrayToBigInteger(byteArray, 168));
        }

        if (!("amountNQT" in data)) {
            data.amountNQT = "0";
        }

        if (!("recipient" in data)) {
            //recipient == genesis
            data.recipient = "0";
            data.recipientRS = "BURST-2222-2222-2222-22222";
        }

        if (transaction.publicKey != BRS.accountInfo.publicKey) {
            return false;
        }

        if (transaction.deadline !== data.deadline) {
            return false;
        }

        requestTypeWithoutRecipientInData = ["buyAlias", "dgsPurchase", "dgsRefund", "dgsDelivery", "dgsFeedback"]
        if ( ! ( requestTypeWithoutRecipientInData.indexOf(requestType) + 1) && transaction.recipient !== data.recipient) {
            if (data.recipient == "1739068987193023818" && transaction.recipient == "0") {
                //ok
            } else {
                return false;
            }
        }

        requestTypeWithSeperatedAmountNQTCalculation = ["sendMoneyMulti", "sendMoneyMultiSame", "sendMoneyEscrow" ];
        if ( transaction.feeNQT !== data.feeNQT || ( ! (requestTypeWithSeperatedAmountNQTCalculation.indexOf(requestType)+1) && transaction.amountNQT !== data.amountNQT ) ) {
            return false;
        }

        if ("referencedTransactionFullHash" in data) {
            if (transaction.referencedTransactionFullHash !== data.referencedTransactionFullHash) {
                return false;
            }
        } else if (transaction.referencedTransactionFullHash !== "") {
            return false;
        }

        if (transaction.version > 0) {
            //has empty attachment, so no attachmentVersion byte...
            if (requestType == "sendMoney" || requestType == "sendMessage") {
                pos = 176;
            } else {
                pos = 177;
            }
        } else {
            pos = 160;
        }

        var requestTypeOf = {
            "sendMoney": { "type": 0, "subtype": 0 },
            "sendMoneyMulti": {
                "type":    0,
                "subtype": 1,
                "parse":   function () { return [
                    [ "Byte", "Long*$0", "Long*$0"],
                    [
                        data.recipients.split(";").length,
                        data.recipients.split(";").splice(0, data.recipients.split(";").length / 2).join("").replace(":", ""),
                        data.recipients.split(";").splice(data.recipients.split(";").length / 2).join("").replace(":", "")
                    ]
                ];}
            },
            "sendMoneyMultiSame": {
                "type":    0,
                "subtype": 2,
                "parse":   function () { return [
                    [ "Byte", "Long*$0"],
                    [
                        data.recipients.split(";").length,
                        data.recipients.split(";").join("")
                    ]
                ];},
                "postCheck": function(parsedValues) {
                    return transaction.amountNQT === "" + ( parsedValues[0] * data.amountNQT );
                }
            },
            "sendMessage": { "type": 1, "subtype": 0 },
            "setAlias": {
                "type":    1,
                "subtype": 1,
                "parse":   function () { return [
                    [
                        "Byte", "String*$0",
                        "Short", "String*$2"
                    ],
                    [
                        data.aliasName.length, data.aliasName,
                        data.aliasURI.length, data.aliasURI
                    ],
                    [
                        false, "aliasName",
                        false, "aliasURI"
                    ]
                ];}
            },
            "setAccountInfo": {
                "type":    1,
                "subtype": 5,
                "parse":   function () { return [
                    [
                        "Byte", "String*$0",
                        "Short", "String*$2"
                    ],
                    [
                        data.name.length, data.name,
                        data.description.length, data.description
                    ],
                    [
                        false, "name",
                        false, "description"
                    ]
                ];}
            },
            "sellAlias": {
                "type":    1,
                "subtype": 6,
                "parse":   function () { return [
                    [ "Byte", "String*$0", "Long" ],
                    [ data.aliasName.length, data.aliasName, data.priceNQT ],
                    [ false, "alias", "priceNQT" ]
                ];}
            },
            "buyAlias": {
                "type":    1,
                "subtype": 7,
                "parse":   function () { return [
                    [ "Byte", "String*$0" ],
                    [ data.aliasName.length, data.aliasName ],
                    [ false, "alias" ]
                ];}
            },

            "issueAsset": {
                "type":    2,
                "subtype": 0,
                "parse":   function () { return [
                    [
                        "Byte", "String*$0",
                        "Short", "String*$2",
                        "Long",
                        "Byte"
                    ],
                    [
                        data.name.length, data.name,
                        data.description.length, data.description,
                        data.quantityQNT,
                        data.decimals
                    ],
                    [
                        false, "name",
                        false, "description",
                        "quantityQNT",
                        "decimals"
                    ]
                ];}
            },
            "transferAsset": {
                "type":    2,
                "subtype": 1,
                "parse":   [
                    [ "Long", "Long" ],
                    [ data.asset, data.quantityQNT ],
                    [ "asset", "quantityQNT" ]
                ]
            },
            "placeAskOrder": {
                "type":    2,
                "subtype": 2,
                "parse":   [
                    [ "Long", "Long", "Long" ],
                    [ data.asset, data.quantityQNT, data.priceNQT ],
                    [ "asset", "quantityQNT", "priceNQT" ]
                ]
            },
            "placeBidOrder": {
                "type":    2,
                "subtype": 3,
                "parse":   [
                    [ "Long", "Long", "Long" ],
                    [ data.asset, data.quantityQNT, data.priceNQT ],
                    [ "asset", "quantityQNT", "priceNQT" ]
                ]
            },
            "cancelAskOrder": {
                "type":    2,
                "subtype": 4,
                "parse":   [
                    [ "Long" ],
                    [ data.order ],
                    [ "order" ]
                ]
            },
            "cancelBidOrder": {
                "type":    2,
                "subtype": 5,
                "parse":   [
                    [ "Long" ],
                    [ data.order ],
                    [ "order" ]
                ]
            },

            "dgsListing": {
                "type":    3,
                "subtype": 0,
                "parse":   function () { return [
                    [
                        "Short", "String*$0",
                        "Short", "String*$2",
                        "Short", "String*$4",
                        "Int",
                        "Long"
                    ],
                    [
                        data.name.length, data.name,
                        data.description.length, data.description,
                        data.tags.length, data.tags,
                        data.quantity,
                        data.priceNQT
                    ],
                    [
                        false, "name",
                        false, "description",
                        false, "tags",
                        "quantity",
                        "priceNQT"
                    ]
                ];}
            },
            "dgsDelisting": {
                "type":    3,
                "subtype": 1,
                "parse":   [
                    [ "Long" ],
                    [ data.goods ],
                    [ "goods" ]
                ]
            },
            "dgsPriceChange": {
                "type":    3,
                "subtype": 2,
                "parse":   [
                    [ "Long", "Long" ],
                    [ data.goods, data.priceNQT ],
                    [ "goods", "priceNQT" ]
                ]
            },
            "dgsQuantityChange": {
                "type":    3,
                "subtype": 3,
                "parse":   [
                    [ "Long", "Int" ],
                    [ data.goods, data.deltaQuantity ],
                    [ "goods", "deltaQuantity" ]
                ]
            },
            "dgsPurchase": {
                "type":    3,
                "subtype": 4,
                "parse":   [
                    [ "Long", "Int", "Long", "Int" ],
                    [ data.goods, data.quantity, data.priceNQT, data.deliveryDeadlineTimestamp ],
                    [ "goods", "quantity", "priceNQT", "deliveryDeadlineTimestamp" ]
                ]
            },
           "dgsDelivery": {
                "type":    3,
                "subtype": 5,
                "parse":   function () {
                    // very ugly hack - I do not know, what horse was ridden during designing this transaction type
                    var encryptedGoodsLength = converters.byteArrayToSignedShort(byteArray, pos + 8);
                    var goodsLength          = converters.byteArrayToSignedInt32(byteArray, pos + 8);
                    transaction.goodsIsText  = goodsLength < 0;

                    return [
                        [ "Long", "Int", "Hex*" + encryptedGoodsLength, "Hex*32", "Long" ],
                        [

                            data.purchase,
                            ( transaction.goodsIsText ? ( data.goodsData.length / 2 ) | -2147483648 : data.goodsData.length / 2 ),
                            data.goodsData,
                            data.goodsNonce,
                            data.discountNQT,

                        ],
                        [ "purchase", "goodsLength", "goodsData", "goodsNonce", "discountNQT" ]
                    ];
                },
                "postCheck": function(parsedValues) {
                    return ( transaction.goodsIsText ? "true" : "false" ) === data.goodsIsText;
                }
            },
            "dgsFeedback": {
                "type":    3,
                "subtype": 6,
                "parse":   [
                    [ "Long" ],
                    [ data.purchase ],
                    [ "purchase" ]
                ]
            },
            "dgsRefund": {
                "type":    3,
                "subtype": 7,
                "parse":   [
                    [ "Long", "Long" ],
                    [ data.purchase, data.refundNQT ],
                    [ "purchase", "refundNQT" ]
                ]
            },
            "leaseBalance": {
                "type":    4,
                "subtype": 0,
                "parse":   [
                    [ "Short"],
                    [ data.period ],
                    [ "period" ]
                ]
            },
            "setRewardRecipient": { "type": 20, "subtype": 0 },
            "sendMoneyEscrow": {
                "type":    21,
                "subtype": 0,
                "parse":   function () { return [
                    [ "Long", "Int", "Byte", "Byte", "Byte", "Long*$3"],
                    [
                        data.amountNQT,
                        data.escrowDeadline,
                        ["undecided", "release", "refund", "split"].indexOf(data.deadlineAction),
                        data.requiredSigners,
                        data.signers.split(";").length,
                        data.signers.split(";").join("")
                    ]
                ];}
            },
            "escrowSign": {
                "type":    21,
                "subtype": 1,
                "parse":   function () { return [
                    [ "Long", "Byte"],
                    [
                        data.escrow,
                        ["undecided", "release", "refund", "split"].indexOf(data.decision)
                    ]
                ];}
            },
            "sendMoneySubscription": {
                "type":    21,
                "subtype": 3,
                "parse":   [
                    [ "Int" ],
                    [ data.frequency ]
                ]
            },
            "subscriptionCancel": {
                "type":    21,
                "subtype": 4,
                "parse":   [
                    [ "Long" ],
                    [ data.subscription ]
                ]
            }
        };

        if ( requestTypeOf[requestType] ) {
            var spec = requestTypeOf[requestType];
            if (transaction.type !== spec.type || transaction.subtype !== spec.subtype ) {
                return false;
            }
            var parsedValues = [];
            if ( spec.parse ) {
                var parse = Array.isArray(spec.parse) ? spec.parse : spec.parse();
                for (var i = 0; i < parse[0].length; i++) {
                    // typeSpec contains the type and a possible factor found by index
                    var typeSpec = parse[0][i].split("*");
                    typeSpec[1] = parseInt(
                        typeSpec[1]
                            ? (
                                typeSpec[1].replace("$", "") == typeSpec[1]
                                    ? typeSpec[1]                            // fixed length
                                    : parsedValues[typeSpec[1].replace("$", "")] // variable length, depending on other element
                            )
                            : 1
                    );

                    var currentParsed = "";
                    switch (typeSpec[0]) {
                        case "String":
                            currentParsed = converters.byteArrayToString(byteArray, pos, typeSpec[1]);
                            pos += typeSpec[1];
                            break;
                        case "Hex":
                            currentParsed = converters.byteArrayToHexString(byteArray.slice(pos, pos + typeSpec[1]));
                            pos += typeSpec[1];
                            break;
                        default:
                            for ( var amount = 0; amount < typeSpec[1]; amount++ ) {
                                switch (typeSpec[0]) {
                                    case "Long":
                                        currentParsed += converters.byteArrayToBigInteger(byteArray, pos).toString();
                                        pos += 8;
                                        break;
                                    case "Int":
                                        currentParsed += String(converters.byteArrayToSignedInt32(byteArray, pos));
                                        pos += 4;
                                        break;
                                    case "Short":
                                        currentParsed += String(converters.byteArrayToSignedShort(byteArray, pos));
                                        pos += 2;
                                        break;
                                    case "Byte":
                                        currentParsed += String(parseInt(byteArray[pos++], 10));
                                        break;
                                    default:
                                        return false;
                                }
                            }
                    }
                    if ( String(currentParsed) !== String(parse[1][i]) ) {
                        return false;
                    }
                    if ( parse[2] && parse[2][i] ) {
                        transaction[parse[2][i]] = String(currentParsed);
                    }
                    parsedValues.push(currentParsed);
                }
            }
            if ( spec.postCheck ) {
                if ( ! ( spec.postCheck(parsedValues) ) ) {
                    return false;
                }
            }
        }
        else {
            //invalid requestType..
            return false;
        }

        var position = 1;

        //non-encrypted message
        if ((transaction.flags & position) !== 0 || (requestType == "sendMessage" && data.message)) {
            var attachmentVersion = byteArray[pos];

            pos++;

            var messageLength = converters.byteArrayToSignedInt32(byteArray, pos);

            transaction.messageIsText = messageLength < 0; // ugly hack??

            if (messageLength < 0) {
                messageLength &= 2147483647;
            }

            pos += 4;

            if (transaction.messageIsText) {
                transaction.message = converters.byteArrayToString(byteArray, pos, messageLength);
            } else {
                var slice = byteArray.slice(pos, pos + messageLength);
                transaction.message = converters.byteArrayToHexString(slice);
            }

            pos += messageLength;

            var messageIsText = (transaction.messageIsText ? "true" : "false");

            if (messageIsText != data.messageIsText) {
                return false;
            }

            if (transaction.message !== data.message) {
                return false;
            }
        } else if (data.message) {
            return false;
        }

        position <<= 1;

        //encrypted note
        if ((transaction.flags & position) !== 0) {
            var attachmentVersion = byteArray[pos];

            pos++;

            var encryptedMessageLength = converters.byteArrayToSignedInt32(byteArray, pos);

            transaction.messageToEncryptIsText = encryptedMessageLength < 0;

            if (encryptedMessageLength < 0) {
                encryptedMessageLength &= 2147483647;
            }

            pos += 4;

            transaction.encryptedMessageData = converters.byteArrayToHexString(byteArray.slice(pos, pos + encryptedMessageLength));

            pos += encryptedMessageLength;

            transaction.encryptedMessageNonce = converters.byteArrayToHexString(byteArray.slice(pos, pos + 32));

            pos += 32;

            var messageToEncryptIsText = (transaction.messageToEncryptIsText ? "true" : "false");

            if (messageToEncryptIsText != data.messageToEncryptIsText) {
                return false;
            }

            if (transaction.encryptedMessageData !== data.encryptedMessageData || transaction.encryptedMessageNonce !== data.encryptedMessageNonce) {
                return false;
            }
        } else if (data.encryptedMessageData) {
            return false;
        }

        position <<= 1;

        if ((transaction.flags & position) !== 0) {
            var attachmentVersion = byteArray[pos];

            pos++;

            var recipientPublicKey = converters.byteArrayToHexString(byteArray.slice(pos, pos + 32));

            if (recipientPublicKey != data.recipientPublicKey) {
                return false;
            }
            pos += 32;
        } else if (data.recipientPublicKey) {
            return false;
        }

        position <<= 1;

        if ((transaction.flags & position) !== 0) {
            var attachmentVersion = byteArray[pos];

            pos++;

            var encryptedToSelfMessageLength = converters.byteArrayToSignedInt32(byteArray, pos);

            transaction.messageToEncryptToSelfIsText = encryptedToSelfMessageLength < 0;

            if (encryptedToSelfMessageLength < 0) {
                encryptedToSelfMessageLength &= 2147483647;
            }

            pos += 4;

            transaction.encryptToSelfMessageData = converters.byteArrayToHexString(byteArray.slice(pos, pos + encryptedToSelfMessageLength));

            pos += encryptedToSelfMessageLength;

            transaction.encryptToSelfMessageNonce = converters.byteArrayToHexString(byteArray.slice(pos, pos + 32));

            pos += 32;

            var messageToEncryptToSelfIsText = (transaction.messageToEncryptToSelfIsText ? "true" : "false");

            if (messageToEncryptToSelfIsText != data.messageToEncryptToSelfIsText) {
                return false;
            }

            if (transaction.encryptToSelfMessageData !== data.encryptToSelfMessageData || transaction.encryptToSelfMessageNonce !== data.encryptToSelfMessageNonce) {
                return false;
            }
        } else if (data.encryptToSelfMessageData) {
            return false;
        }

        return transactionBytes.substr(0, 192) + signature + transactionBytes.substr(320);
    };

    BRS.broadcastTransactionBytes = function(transactionData, callback, originalResponse, originalData) {
        $.ajax({
            url: BRS.server + "/burst?requestType=broadcastTransaction",
            crossDomain: true,
            dataType: "json",
            type: "POST",
            timeout: 30000,
            async: true,
            data: {
                "transactionBytes": transactionData
            }
        }).done(function(response, status, xhr) {
            if (BRS.console) {
                BRS.addToConsole(this.url, this.type, this.data, response);
            }

            if (callback) {
                if (response.errorCode) {
                    if (!response.errorDescription) {
                        response.errorDescription = (response.errorMessage ? response.errorMessage : "Unknown error occured.");
                    }
                    callback(response, originalData);
                } else if (response.error) {
                    response.errorCode = 1;
                    response.errorDescription = response.error;
                    callback(response, originalData);
                } else {
                    if ("transactionBytes" in originalResponse) {
                        delete originalResponse.transactionBytes;
                    }
                    originalResponse.broadcasted = true;
                    originalResponse.transaction = response.transaction;
                    originalResponse.fullHash = response.fullHash;
                    callback(originalResponse, originalData);
                    if (originalData.referencedTransactionFullHash) {
                        $.notify($.t("info_referenced_transaction_hash"), {
                            type: 'info',
                            offset: {
                                   x: 5,
                                   y: 60
                                    }
                        });
                    }
                }
            }
        }).fail(function(xhr, textStatus, error) {
            if (BRS.console) {
                BRS.addToConsole(this.url, this.type, this.data, error, true);
            }

            if (callback) {
                if (error == "timeout") {
                    error = $.t("error_request_timeout");
                }
                callback({
                    "errorCode": -1,
                    "errorDescription": error
                }, {});
            }
        });
    };

    return BRS;
}(BRS || {}, jQuery));

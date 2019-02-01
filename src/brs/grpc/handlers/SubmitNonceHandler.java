package brs.grpc.handlers;

import brs.Account;
import brs.Blockchain;
import brs.Generator;
import brs.crypto.Crypto;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.ApiException;
import brs.grpc.proto.Brs;
import brs.services.AccountService;
import brs.util.Convert;

import java.util.Objects;

public class SubmitNonceHandler implements GrpcApiHandler<Brs.SubmitNonceRequest, Brs.SubmitNonceResponse> {

    private final Blockchain blockchain;
    private final AccountService accountService;
    private final Generator generator;

    public SubmitNonceHandler(Blockchain blockchain, AccountService accountService, Generator generator) {
        this.blockchain = blockchain;
        this.accountService = accountService;
        this.generator = generator;
    }

    @Override
    public Brs.SubmitNonceResponse handleRequest(Brs.SubmitNonceRequest request) throws Exception {
        String secret = request.getSecretPhrase();
        long nonce = request.getNonce();

        String accountId = Convert.toUnsignedLong(request.getAccount());

        int submissionHeight = request.getBlockHeight();

        if (submissionHeight != 0) {
            if (submissionHeight != blockchain.getHeight() + 1) {
                throw new ApiException("Given block height does not match current blockchain height");
            }
        }

        if (Objects.equals(secret, "")) {
            throw new ApiException("Missing Passphrase");
        }

        byte[] secretPublicKey = Crypto.getPublicKey(secret);
        Account secretAccount = accountService.getAccount(secretPublicKey);
        if(secretAccount != null) {
            Account genAccount;
            if(accountId != null) {
                genAccount = accountService.getAccount(Convert.parseAccountId(accountId));
            }
            else {
                genAccount = secretAccount;
            }

            if(genAccount != null) {
                Account.RewardRecipientAssignment assignment = accountService.getRewardRecipientAssignment(genAccount);
                long rewardId;
                if (assignment == null) {
                    rewardId = genAccount.getId();
                }
                else if (assignment.getFromHeight() > blockchain.getLastBlock().getHeight() + 1) {
                    rewardId = assignment.getPrevRecipientId();
                }
                else {
                    rewardId = assignment.getRecipientId();
                }
                if (rewardId != secretAccount.getId()) {
                    throw new ApiException("Passphrase does not match reward recipient");
                }
            }
            else {
                throw new ApiException("Passphrase is for a different account");
            }
        }

        Generator.GeneratorState generatorState;
        if (accountId == null || secretAccount == null) {
            generatorState = generator.addNonce(secret, nonce);
        }
        else {
            Account genAccount = accountService.getAccount(Convert.parseUnsignedLong(accountId));
            if (genAccount == null || genAccount.getPublicKey() == null) {
                throw new ApiException("Passthrough mining requires public key in blockchain");
            }
            else {
                byte[] publicKey = genAccount.getPublicKey();
                generatorState = generator.addNonce(secret, nonce, publicKey);
            }
        }

        if (generatorState == null) {
            throw new ApiException("Failed to create generator");
        }

        return Brs.SubmitNonceResponse.newBuilder().setDeadline(generatorState.getDeadline().longValueExact()).build();
    }
}
